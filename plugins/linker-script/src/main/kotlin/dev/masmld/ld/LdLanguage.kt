package dev.masmld.ld

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader

object LdLanguage : Language("MASMLD GNU ld")
object LdFileType : LanguageFileType(LdLanguage) {
    override fun getName() = "MASMLD Linker Script"
    override fun getDescription() = "GNU ld linker script"
    override fun getDefaultExtension() = "ld"
    override fun getIcon() = FILE_ICON
}

private val FILE_ICON = IconLoader.getIcon("/icons/linker.svg", LdFileType::class.java)
