package dev.masmld.ld.highlighting

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.masmld.ld.LdFileType

class LdColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "MASMLD GNU ld"
    override fun getIcon() = LdFileType.icon
    override fun getHighlighter() = LdSyntaxHighlighter()
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, com.intellij.openapi.editor.colors.TextAttributesKey>? = null
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Command and attribute", LdColors.COMMAND),
        AttributesDescriptor("Builtin function", LdColors.FUNCTION),
        AttributesDescriptor("Section pattern and dotted symbol", LdColors.SECTION),
        AttributesDescriptor("Location counter", LdColors.LOCATION),
        AttributesDescriptor("Number", LdColors.NUMBER),
        AttributesDescriptor("String and quoted name", LdColors.STRING),
        AttributesDescriptor("Block comment", LdColors.COMMENT),
        AttributesDescriptor("Hash line", LdColors.HASH_LINE),
        AttributesDescriptor("Operator and wildcard", LdColors.OPERATOR),
        AttributesDescriptor("Braces", LdColors.BRACES),
        AttributesDescriptor("Parentheses", LdColors.PARENTHESES),
        AttributesDescriptor("Punctuation", LdColors.PUNCTUATION),
    )
    override fun getDemoText() = """
        /* GNU ld layout */
        ENTRY(_start)
        MEMORY { RAM (rwx) : ORIGIN = 0x80000000, LENGTH = 128M }
        SECTIONS {
            . = ORIGIN(RAM);
            .text : { KEEP(*(.text.entry)) *(.text .text.*) } > RAM
            . = ALIGN(4K);
            .bss (NOLOAD) : { *(.bss .bss.*) } > RAM
            ASSERT(SIZEOF(.text) < LENGTH(RAM), "text too large");
            /DISCARD/ : { *(.eh_frame) }
        }
    """.trimIndent()
}
