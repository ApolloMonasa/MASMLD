package dev.masmld.riscv

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.ChooseByNameContributorEx2
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import dev.masmld.riscv.symbols.SourceSymbolBridge
import org.junit.Assert.*
import org.junit.Test

class SourceSymbolBridgeTest {
    private val origin = object : FakePsiElement() {
        override fun getParent(): PsiElement? = null
        override fun getName() = "assembly use"
        override fun isValid() = true
    }
    private class NativeItem(private val symbol: String = "c_entry", private val navigatable: Boolean = true) : NavigationItem {
        var navigated = false
        override fun getName() = symbol
        override fun getPresentation(): ItemPresentation = object : ItemPresentation {
            override fun getPresentableText() = symbol
            override fun getLocationString() = "entry.cpp"
            override fun getIcon(unused: Boolean): javax.swing.Icon? = null
        }
        override fun navigate(requestFocus: Boolean) { navigated = true }
        override fun canNavigate() = navigatable
        override fun canNavigateToSource() = navigatable
    }
    @Test fun `non PSI C++ backend items remain navigatable reference targets`() {
        val item = NativeItem()
        val target = SourceSymbolBridge.adapt(origin, "c_entry", item)
        assertTrue(target is NavigatablePsiElement)
        assertEquals("c_entry", (target as com.intellij.psi.PsiNamedElement).name)
        assertEquals("entry.cpp", (target as ItemPresentation).locationString)
        (target as NavigatablePsiElement).navigate(true)
        assertTrue(item.navigated)
    }
    @Test fun `unrelated names and unavailable native destinations are ignored`() {
        assertNull(SourceSymbolBridge.adapt(origin, "other", NativeItem()))
        assertNull(SourceSymbolBridge.adapt(origin, "c_entry", NativeItem(navigatable = false)))
    }
    @Test fun `clangd style providers are primed before item resolution`() {
        val parameters = FindSymbolParameters("c_entry", "c_entry", GlobalSearchScope.EMPTY_SCOPE)
        val item = NativeItem()
        var primed = false
        val provider = object : ChooseByNameContributorEx2 {
            override fun processNames(processor: Processor<in String>, parameters: FindSymbolParameters) {
                assertEquals("c_entry", parameters.completePattern)
                primed = true
                processor.process("c_entry")
            }
            override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) = error("use pattern-aware Ex2 lookup")
            override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
                assertTrue("backend result cache must be populated", primed)
                processor.process(item)
            }
        }
        val results = mutableListOf<NavigationItem>()
        SourceSymbolBridge.query(provider, "c_entry", parameters, Processor { results += it; true })
        assertEquals(listOf(item), results)
    }
    @Test fun `direct Rust style indexes do not enumerate every project symbol`() {
        val item = NativeItem("rust_main")
        val provider = object : ChooseByNameContributorEx {
            override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) = error("unnecessary global enumeration")
            override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
                assertEquals("rust_main", name)
                processor.process(item)
            }
        }
        val results = mutableListOf<NavigationItem>()
        SourceSymbolBridge.query(provider, "rust_main", FindSymbolParameters("rust_main", "rust_main", GlobalSearchScope.EMPTY_SCOPE), Processor { results += it; true })
        assertEquals(listOf(item), results)
    }
    @Test fun `qualified C++ signature names are queried with their indexed spelling`() {
        val indexed = "kernel::c_entry(int)"
        val item = NativeItem(indexed)
        val provider = object : ChooseByNameContributorEx2 {
            override fun processNames(processor: Processor<in String>, parameters: FindSymbolParameters) { processor.process(indexed) }
            override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) = error("use Ex2")
            override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
                assertEquals(indexed, name)
                processor.process(item)
            }
        }
        val targets = mutableListOf<PsiElement>()
        SourceSymbolBridge.query(provider, "c_entry", FindSymbolParameters("c_entry", "c_entry", GlobalSearchScope.EMPTY_SCOPE), Processor {
            SourceSymbolBridge.adapt(origin, "c_entry", it)?.let(targets::add)
            true
        })
        assertEquals(1, targets.size)
        (targets.single() as NavigatablePsiElement).navigate(true)
        assertTrue(item.navigated)
    }

}
