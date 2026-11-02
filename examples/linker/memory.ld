/* Standalone GNU ld example: no assembly architecture setting is required. */
ENTRY(_start)
MEMORY {
    RAM (rwx) : ORIGIN = 0x80000000, LENGTH = 128M
}
SECTIONS {
    . = ORIGIN(RAM);
    .text : {
        KEEP(*(.text.entry))
        *(.text .text.*)
    } > RAM
    . = ALIGN(4K);
    .rodata : { *(.rodata .rodata.*) } > RAM
    .data : { *(.data .data.*) } > RAM
    .bss (NOLOAD) : {
        __bss_start = .;
        *(.bss .bss.* COMMON)
        __bss_end = .;
    } > RAM
    PROVIDE(__stack_top = ORIGIN(RAM) + LENGTH(RAM));
    ASSERT(__bss_end <= __stack_top, "RAM overflow");
    /DISCARD/ : { *(.eh_frame) }
}
