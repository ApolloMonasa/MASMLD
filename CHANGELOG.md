# Changelog

## 0.9.0-dev — Unreleased

- Inspect GNU ld map addresses and input objects, with navigation to matching linker-script definitions.
- Pinpoint invalid RISC-V operands and check absolute immediate, relocation and compressed-offset constraints.
- Use statically known GNU as conditional branches for RISC-V completion and navigation, retaining possible targets for unknown conditions.
- Check GNU as macro arguments and preview static macro expansion with navigation to the definition.

## 0.8.0 — 2026-11-03

- Promote the 0.8 release candidates after RustRover editor acceptance.

## 0.8.0-rc.3 — 2026-11-03

- Return to the first column on Enter after a RISC-V .include directive.

## 0.8.0-rc.2 — 2026-11-03

- Indent one level on Enter after standalone RISC-V labels, using the editor code style.

## 0.8.0-rc.1 — 2026-11-03

- Add per-file/directory/glob architecture overrides, profile and GNU target validation, include paths, and a buildable optional architecture tutorial.
- Add contextual RISC-V completion, parameter information, project exports index, static include/macro navigation and ISA scopes.
- Extend the offline catalog to 467 names, including M/A/F/D/C, Zicsr/Zifencei, floating registers and CSR aliases; add XLEN/operand diagnostics and an ISA quick fix.
- Add Find Usages, rename conflict checks and grouped assignment renames, numeric label usage search, structure views, folding and conservative formatting.
- Add GNU ld symbol/region/section/REGION_ALIAS navigation, INCLUDE resolution, completion, checked constant expressions and layout hints.
- Add Rust/C/C++ export aliases, name demangling, optional exact binary-symbol indexing and source-line navigation through debug information.
- Add platform editor integration tests and actual C/C++/Rust, ELF and RISC-V assembler protocol checks.
- Fix directive completion around leading dots and existing suffixes, dotted label completion, and Enter indentation around macro blocks.
- Resolve macros through sibling assembly includes and recognize GNU as alternate macro directives.

## 0.7.0 — 2026-11-03

First public release, licensed under MIT and published by ApolloMonasa.

- Independent Core, RISC-V and GNU ld plugins for IntelliJ Platform IDEs; RustRover is the primary tested product.
- RISC-V highlighting and offline Chinese documentation for 181 names; GNU ld highlighting and 48 documentation entries.
- Dedicated assembly and linker-script file icons; blue/purple modular cube plugin branding.
- Navigation to assembly labels, numeric local labels, constants and exported assembly definitions.
- External source-symbol lookup through the host IDE, including Rust and C/C++ providers when available.
- New: same-file `.macro` call and `.purgem` navigation, including redefinition and recursive calls.
- User reports completing real IDE feature acceptance and installation, upgrade and module-combination checks for the preceding 0.6.0-dev build. New macro navigation is covered by automated tests; it has not received separate user GUI acceptance.

Known limits: build 262 or newer; binary compatibility checked with RustRover and CLion. No macro expansion, include/preprocessor evaluation, C++ name demangling, linker-script symbol navigation, RV32/RV64 legality checking, x86 or ARM module yet.

## Development previews

0.1–0.6 introduced modular architecture, RISC-V syntax support, offline documentation, standalone GNU ld support, release verification, file icons and source-symbol navigation.
