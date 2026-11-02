package dev.masmld.core.language

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import dev.masmld.core.icons.AssemblyIcons

object AssemblyLanguage : Language("MASMLD Assembly")

object AssemblyFileType : LanguageFileType(AssemblyLanguage) {
    override fun getName() = "MASMLD Assembly"
    override fun getDescription() = "Assembly source (architecture selected in MASMLD settings)"
    override fun getDefaultExtension() = "s"
    override fun getIcon() = AssemblyIcons.FILE
}
