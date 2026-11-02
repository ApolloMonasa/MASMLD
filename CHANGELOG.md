# Changelog

## 0.8.0-dev — Unreleased

- Add per-file/directory/glob architecture overrides, profile and GNU target validation, include paths, and a buildable optional architecture tutorial.
- Add contextual RISC-V completion, parameter information, project exports index, static include/macro navigation and ISA scopes.
- Extend the offline catalog to 465 names, including M/A/F/D/C, Zicsr/Zifencei, floating registers and CSR aliases; add XLEN/operand diagnostics and an ISA quick fix.
- Add Find Usages, rename conflict checks and grouped assignment renames, numeric label usage search, structure views, folding and conservative formatting.
- Add GNU ld symbol/region/section/REGION_ALIAS navigation, INCLUDE resolution, completion, checked constant expressions and layout hints.
- Add Rust/C/C++ export aliases, name demangling, optional exact binary-symbol indexing and source-line navigation through debug information.
- Add platform editor integration tests and actual C/C++/Rust, ELF and RISC-V assembler protocol checks.

## 0.7.0 — 2026-11-03

First public release, licensed under MIT and published by ApolloMonasa.

- Independent Core, RISC-V and GNU ld plugins for IntelliJ Platform IDEs; RustRover is the primary tested product.
- RISC-V highlighting and offline Chinese documentation for 181 names; GNU ld highlighting and 48 documentation entries.
- Dedicated assembly and linker-script file icons; blue/purple modular cube plugin branding.
- Navigation to assembly labels, numeric local labels, constants and exported assembly definitions.
- External source-symbol lookup through the host IDE, including Rust and C/C++ providers when available.
- New: same-file `.macro` call and `.purgem` navigation, including redefinition and recursive calls.
- User reports completing real IDE feature acceptance and installation, upgrade and module-combination checks for the preceding 0.6.0-dev build. New macro navigation is covered by automated tests; it has not received separate user GUI acceptance.

Known limits: build 262 or newer; tested binary compatibility with the RustRover and CLion builds documented in docs/validation.md. No macro expansion, include/preprocessor evaluation, C++ name demangling, linker-script symbol navigation, RV32/RV64 legality checking, x86 or ARM module yet.

## Development previews

0.1–0.6 introduced modular architecture, RISC-V syntax support, offline documentation, standalone GNU ld support, release verification, file icons and source-symbol navigation. Historical evidence remains in docs/validation.md.
