.data
x: .word 0
#-----MAIN LOOP -----
.text
.globl main
main:
li $a0 , 5
jal fibonacci
la $s0 , x
sw $v0 , 0($s0)
lw $t0 , x
move $a0 , $t0
li $v0 , 1
syscall
li $v0 , -1
syscall
li $v0, 10
syscall
#-----MAIN TERMINATION-----
#-----FUNCTIONS-----
fibonacci:
	sub $sp, $sp, 8
	sw $a0 , 0($sp)
	sw $ra , 4($sp)
	li $t0 , 1
	slt $t1 , $a0 , $t0
	beq $t1 , $zero , L_0
	bne $t1 , $zero , L_1
	L_0:
		move $v0 , $a0
		add $sp, $sp, 8
		jr $ra
	L_1:
	addi $t1 , $a0 , -1
	move $a0 , $t1
	jal fibonacci
	lw $a0 , 0($sp)
	move $t1 , $v0
	addi $t0 , $a0 , -2
	move $a0 , $t0
	jal fibonacci
	move $t0 , $v0
	add $t0, $t1, $t0
	move $v0 , $t0
	add $sp, $sp, 8
	jr $ra
