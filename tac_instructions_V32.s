.data
_V_resultado: .word 2
_V_a: .word 1
_V_b: .word 1
#-----MAIN LOOP -----
.text
.globl main
main:
li $a0 , 5
jal fibonacci
la $s0 , _V_resultado
sw $v0 , 0($s0)
lw $t0 , _V_resultado
move $a0 , $t0
li $v0 , 1
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
	beq $a0 , $t0 , L_0
	slt $t1 , $a0 , $t0
	bne $t1 , $zero , L_0
	beq $t1 , $zero , L_2
	L_0:
		li $v0 , 1
		lw $ra , 4($sp)
		add $sp, $sp, 8
		jr $ra
	L_2:
		addi $t1 , $a0 , -1
		move $a0 , $t1
		jal fibonacci
		lw $a0 , 0($sp)
		lw $ra , 4($sp)
		la $s0 , _V_a
		sw $v0 , 0($s0)
		addi $t1 , $a0 , -2
		move $a0 , $t1
		jal fibonacci
		lw $a0 , 0($sp)
		lw $ra , 4($sp)
		la $s0 , _V_b
		sw $v0 , 0($s0)
		lw $t1 , _V_a
		lw $t0 , _V_b
		add $t2, $t1, $t0
		move $v0 , $t2
		lw $ra , 4($sp)
		add $sp, $sp, 8
		jr $ra
	L_1:
