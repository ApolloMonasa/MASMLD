#include "platform.h"
# Preprocessor lines are displayed, not evaluated.
.section .text
.globl _start
_start:
    li a0, 42
    addi sp, sp, -16
    sd ra, 8(sp)
    la a1, message
    beq a0, zero, 1f
    lw a2, %lo(message)(a1)
1:  nop; ret

# These names are symbols in this context.
add: j a0
a0:  .word 0x2a, 0b1010, 'A

.section .rodata
message: .asciz "message # ; /* inside string */"
/* A block comment
   containing add a0, a1, a2 */
