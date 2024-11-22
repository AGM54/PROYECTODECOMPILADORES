.data
x: .word 25
#-----MAIN LOOP -----
.text
.globl main
main:
li $a0 , 5
jal factorial
la $s0 , x
sw $v0 , 0($s0)
lw $t1 , x
move $a0 , $t1
li $v0 , 1
syscall
li $v0, 10
syscall
#-----MAIN TERMINATION-----
#-----FUNCTIONS-----
factorial:
	sub $sp, $sp, 8
	sw $a0 , 0($sp)
	sw $ra , 4($sp)
	li $t0 , 0
	beq $a0 , $t0 , L_0
	bne $a0 , $t0 , L_1
	L_0:
		li $v0 , 1
		lw $ra , 4($sp)
		add $sp, $sp, 8
		jr $ra
	L_1:
	addi $t0 , $a0 , -1
	move $a0 , $t0
	jal factorial
	lw $a0 , 0($sp)
	lw $ra , 4($sp)
	move $t0 , $v0
	mul $t0, $a0, $t0
	move $v0 , $t0
	lw $ra , 4($sp)
	add $sp, $sp, 8
	jr $ra
