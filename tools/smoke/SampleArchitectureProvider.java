package dev.masmld.smoke;

import com.intellij.lang.Language;
import dev.masmld.core.api.ArchitectureProfile;
import dev.masmld.core.api.ArchitectureProvider;
import dev.masmld.core.language.AssemblyLanguage;
import java.util.List;

/** Test-only third-party-style extension. Never included in a release plugin. */
public final class SampleArchitectureProvider implements ArchitectureProvider {
    public static final Language LANGUAGE = new ExampleLanguage();
    private static final class ExampleLanguage extends Language {
        private ExampleLanguage() { super(AssemblyLanguage.INSTANCE, "MASMLD Smoke Example"); }
    }
    @Override public String getId() { return "smoke.example"; }
    @Override public String getDisplayName() { return "Smoke example architecture"; }
    @Override public Language getLanguage() { return LANGUAGE; }
    @Override public List<ArchitectureProfile> getProfiles() { return List.of(new ArchitectureProfile("base", "Base")); }
}
