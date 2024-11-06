.data
x: .word 15
_S_0: .asciiz "yessir"
_S_1: .asciiz "we are fucked"
#-----MAIN LOOP -----
.text
.globl main
main:
la $s1, x
li $t0, 15
sw $t0, 0($s1)
lw $t1, x
li $t2 10
slt $t0 $t2 $t1 
bne $t0 $zero L_0
beq $t0 $zero L_2
li $t2 20
slt $t0 $t2 $t1 
bne $t0 $zero L_0
beq $t0 $zero L_2
L_0:
	la $a0 , _S_0
	li $v0 , 4
	syscall
	j L_1
L_2:
	la $a0 , _S_1
	li $v0 , 4
	syscall
L_1:
li $v0, 10
syscall
#-----MAIN TERMINATION-----
#-----FUNCTIONS-----
