# Third-party notices

MASMLD implementation source, original documentation summaries and brand assets are distributed under LICENSE (MIT). The Gradle wrapper and build dependencies retain their respective upstream licenses. IntelliJ Platform and the host IDE are separate products, not redistributed as part of the plugin ZIPs.

Instruction and linker documentation links reference RISC-V specifications and GNU assembler/linker manuals. These references are factual sources; MASMLD's TSV summaries do not relicense the referenced manuals. Source URLs and version metadata are recorded in the per-module catalog files.

The following standalone test inputs were copied from the publisher's rCore study checkout (`2026a-rcore-ApolloMonasa/os/src/`), whose repository LICENSE is GNU GPL version 3. They retain that license rather than being relicensed under MIT:

- `plugins/arch-riscv/src/test/resources/fixtures/entry.asm`
- `plugins/linker-script/src/test/resources/fixtures/rcore-linker.ld`

The corresponding license is preserved in `third_party/licenses/rcore-GPL-3.0.txt`. These are isolated parser test data and are excluded from installed plugin JARs. References to the fixtures in historical validation records identify test inputs; they do not assign their authorship to MASMLD.
