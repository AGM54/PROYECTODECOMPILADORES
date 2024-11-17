.data
i: .word 0
_S0_ : .asciiz "hello"
#-----MAIN LOOP -----
.text
.globl main
main:
la $s0 , i
li $t0 , 0
sw $t0 , 0($s0)
lw $t0 , i
move $a0 , $t1
li $v0 , 4
syscall
li $v0, 10
syscall
#-----MAIN TERMINATION-----
#-----FUNCTIONS-----
