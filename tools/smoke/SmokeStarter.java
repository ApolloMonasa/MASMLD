package dev.masmld.smoke;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiManager;
import dev.masmld.core.language.AssemblyFileType;
import dev.masmld.core.registry.ArchitectureRegistry;
import dev.masmld.core.settings.AssemblyConfigurable;
import dev.masmld.core.settings.AssemblySettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComboBox;

/** Test-only plugin; never included in release ZIPs. Runs inside the real IDE. */
public final class SmokeStarter implements ApplicationStarter {
    @Override public boolean isHeadless() { return true; }
    @Override public int getRequiredModality() { return NOT_IN_EDT; }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("PASS: " + message);
    }
    @Override public void main(List<String> args) {
        try {
            boolean riscv = Boolean.getBoolean("masmld.smoke.riscv");
            boolean linker = Boolean.getBoolean("masmld.smoke.ld");
            boolean extension = Boolean.getBoolean("masmld.smoke.extension");
            var providers = ArchitectureRegistry.INSTANCE.available();
            check(providers.size() == ((riscv || extension) ? 1 : 0), "optional architecture discovery");
            check(ArchitectureRegistry.INSTANCE.problems().isEmpty(), "architecture registrations satisfy the extension contract");
            if (extension) check(ArchitectureRegistry.INSTANCE.find("smoke.example") != null, "external example discovered without core changes");
            if (riscv) {
                var provider = ArchitectureRegistry.INSTANCE.find("riscv");
                check(provider != null && provider.getProfiles().size() == 2, "RISC-V profiles");
            }
            var project = ProjectManager.getInstance().getDefaultProject();
            var settings = AssemblySettings.Companion.getInstance(project);
            settings.selectArchitecture("");
            String source = "# smoke\n_start:\n    addi a0, zero, 1\n    la a1, target\n    beq a0, zero, 1f\n1: j target\ntarget: ret\n";
            Path path = Path.of(System.getProperty("masmld.smoke.file"));
            Files.writeString(path, source);
            var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
            check(file != null, "source file available");
            check(AssemblyFileType.INSTANCE.getIcon() != null, "assembly file icon registered");
            var app = ApplicationManager.getApplication();
            app.runReadAction(() -> {
                check(file.getFileType() == AssemblyFileType.INSTANCE, "assembly file association");
                var psi = PsiManager.getInstance(project).findFile(file);
                check(psi != null && psi.getLanguage().getID().equals("MASMLD Assembly"), "generic PSI language");
                check(psi.getNode().getText().equals(source), "parser preserves complete source");
            });
            app.invokeAndWait(() -> {
                var ui = new AssemblyConfigurable(project);
                var component = (javax.swing.JPanel) ui.createComponent();
                JComboBox<?> choices = null;
                for (var child : component.getComponents()) {
                    if (child instanceof JComboBox<?> combo) choices = combo;
                }
                check(choices != null && choices.getItemCount() == ((riscv || extension) ? 2 : 1), "settings architecture choices");
                if (riscv || extension) {
                    choices.setSelectedIndex(1);
                    check(ui.isModified(), "settings change detected");
                    WriteAction.run(ui::apply);
                    check(settings.getArchitectureId().equals(extension ? "smoke.example" : "riscv"), "settings apply selection");
                }
                ui.disposeUIResources();
            });
            app.runReadAction(() -> {
                var psi = PsiManager.getInstance(project).findFile(file);
                String expected = extension ? "MASMLD Smoke Example" : (riscv ? "MASMLD RISC-V" : "MASMLD Assembly");
                check(psi != null && psi.getLanguage().getID().equals(expected), "effective PSI language after selection");
                check(psi.getNode().getText().equals(source), "selected parser preserves source");
                if (riscv) {
                    var highlighter = com.intellij.openapi.fileTypes.SyntaxHighlighterFactory.getSyntaxHighlighter(
                        AssemblyFileType.INSTANCE.getLanguage(), project, file);
                    check(highlighter.getClass().getName().equals("dev.masmld.riscv.highlighting.RiscVSyntaxHighlighter"),
                          "shared assembly highlighter dispatches to RISC-V");
                    var lexer = highlighter.getHighlightingLexer();
                    lexer.start("# comment");
                    check(highlighter.getTokenHighlights(lexer.getTokenType()).length > 0, "comment color registered");
                    boolean statementFound = false;
                    for (var node : psi.getNode().getChildren(null)) {
                        if (node.getElementType().toString().equals("RISCV_STATEMENT")) statementFound = true;
                    }
                    check(statementFound, "RISC-V statement AST instantiated");
                    var reference = psi.findReferenceAt(source.indexOf("target"));
                    var declaration = reference == null ? null : reference.resolve();
                    check(declaration instanceof com.intellij.psi.PsiNamedElement
                          && ((com.intellij.psi.PsiNamedElement) declaration).getName().equals("target")
                          && declaration.getTextOffset() == source.lastIndexOf("target"), "assembly reference resolves to exact label PSI");
                    var numericReference = psi.findReferenceAt(source.indexOf("1f"));
                    check(numericReference != null && numericReference.resolve() != null
                          && numericReference.resolve().getTextOffset() == source.indexOf("1:"), "numeric local reference resolves forward");
                    var targets = com.intellij.platform.backend.documentation.DocumentationTargetProvider.EP_NAME
                        .getExtensionList().stream()
                        .flatMap(provider -> provider.documentationTargets(psi, source.indexOf("addi")).stream())
                        .filter(target -> target.computePresentation().getPresentableText().equals("addi"))
                        .toList();
                    check(targets.size() == 1, "documentation target registered for mnemonic");
                    var target = targets.getFirst();
                    check(target.computeDocumentation() != null && target.computeDocumentationHint().contains("示例"),
                          "hover and quick documentation share offline content");
                    check(target.createPointer().dereference() != null, "documentation pointer restores target");
                }
            });
            // A saved selection survives a module being absent on the next IDE startup.
            app.invokeAndWait(() -> WriteAction.run(() -> {
                settings.selectArchitecture("missing-architecture");
                com.intellij.util.FileContentUtil.reparseFiles(project, List.of(file), true);
            }));
            app.runReadAction(() -> {
                var psi = PsiManager.getInstance(project).findFile(file);
                check(psi != null && psi.getLanguage().getID().equals("MASMLD Assembly"), "missing module falls back to generic PSI");
                check(settings.getArchitectureId().equals("missing-architecture"), "missing module selection retained");
            });
            if (linker) {
                String script = "MEMORY { RAM: ORIGIN=0x80000000, LENGTH=128M }\nSECTIONS { . = ALIGN(4K); .text : { *(.text) } > RAM }";
                Path linkerPath = path.resolveSibling("smoke.ld");
                Files.writeString(linkerPath, script);
                var linkerFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(linkerPath);
                app.runReadAction(() -> {
                    check(linkerFile != null && linkerFile.getFileType().getName().equals("MASMLD Linker Script"), "ld file type registered");
                    check(linkerFile.getFileType().getIcon() != null, "ld file icon registered");
                    var psi = PsiManager.getInstance(project).findFile(linkerFile);
                    check(psi != null && psi.getLanguage().getID().equals("MASMLD GNU ld"), "ld language independent of assembly selection");
                    check(psi.getNode().getText().equals(script), "ld AST preserves source");
                    var highlighter = com.intellij.openapi.fileTypes.SyntaxHighlighterFactory.getSyntaxHighlighter(psi.getLanguage(), project, linkerFile);
                    check(highlighter.getClass().getName().equals("dev.masmld.ld.highlighting.LdSyntaxHighlighter"), "ld syntax highlighter registered");
                    var targets = com.intellij.platform.backend.documentation.DocumentationTargetProvider.EP_NAME
                        .getExtensionList().stream()
                        .flatMap(provider -> provider.documentationTargets(psi, script.indexOf("ALIGN")).stream())
                        .filter(target -> target.computePresentation().getPresentableText().equals("ALIGN"))
                        .toList();
                    check(targets.size() == 1 && targets.getFirst().computeDocumentation() != null, "ld offline documentation target registered");
                });
            }
            System.out.println("MASMLD_SMOKE_OK " + (extension ? "external-extension" : (riscv ? "core+riscv" : "core-only")) + (linker ? "+ld" : ""));
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}
