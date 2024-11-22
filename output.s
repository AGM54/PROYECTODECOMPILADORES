.data
_V_resultado: .word 2
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
		move $t1 , $v0
		addi $t0 , $a0 , -2
		move $a0 , $t0
		jal fibonacci
		lw $a0 , 0($sp)
		lw $ra , 4($sp)
		move $t0 , $v0
		add $t0, $t1, $t0
		move $v0 , $t0
		lw $ra , 4($sp)
		add $sp, $sp, 8
		jr $ra
	L_1:
#-----MAIN LOOP -----
.data
_V_resultado: .word 2
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
		move $t1 , $v0
		addi $t0 , $a0 , -2
		move $a0 , $t0
		jal fibonacci
		lw $a0 , 0($sp)
		lw $ra , 4($sp)
		move $t0 , $v0
		add $t0, $t1, $t0
		move $v0 , $t0
		lw $ra , 4($sp)
		add $sp, $sp, 8
		jr $ra
	L_1:
#-----MAIN LOOP -----
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
		move $t1 , $v0
		addi $t0 , $a0 , -2
		move $a0 , $t0
		jal fibonacci
		lw $a0 , 0($sp)
		lw $ra , 4($sp)
		move $t0 , $v0
		add $t0, $t1, $t0
		move $v0 , $t0
		lw $ra , 4($sp)
		add $sp, $sp, 8
		jr $ra
	L_1:
