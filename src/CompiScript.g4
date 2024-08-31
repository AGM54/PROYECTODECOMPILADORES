grammar CompiScript;

// Reglas de inicio
program     : declaration* EOF ;
declaration : classDecl
            | funDecl
            | varDecl
            | statement ;

// Declaraciones de clase, función, variable
classDecl   : 'class' IDENTIFIER ('<' IDENTIFIER)? '{' function* '}' ;
funDecl     : 'fun' function ;
varDecl     : 'var' IDENTIFIER ('=' expression)? ';' ;

// Declaraciones de sentencias
statement   : exprStmt
            | forStmt
            | ifStmt
            | printStmt
            | returnStmt
            | whileStmt
            | block ;

exprStmt    : expression ';' ;
forStmt     : 'for' '(' ( varDecl | exprStmt | ';' ) expression? ';' expression? ')' statement ;
ifStmt      : 'if' '(' expression ')' statement ( 'else' statement )? ;
printStmt   : 'print' expression ';' ;
returnStmt  : 'return' expression? ';' ;
whileStmt   : 'while' '(' expression ')' statement ;
block       : '{' declaration* '}' ;

// Expresiones
expression  : assignment ;
assignment  : IDENTIFIER '=' expression
            | logic_or ; // Separar asignaciones y expresiones aritméticas

logic_or    : logic_and ( 'or' logic_and )* ;
logic_and   : equality ( 'and' equality )* ;
equality    : comparison ( ( '!=' | '==' ) comparison )* ;
comparison  : term ( ( '>' | '>=' | '<' | '<=' ) term )* ;
term        : factor ( ( '-' | '+' ) factor )* ;
factor      : unary ( ( '/' | '*' ) unary )* ;
unary       : ( '!' | '-' ) unary
            | call
            | primary ;  // Añadimos primary directamente en unary

call        : IDENTIFIER '(' arguments? ')' ;  // Manejo adecuado de llamadas a funciones

primary     : 'true'
            | 'false'
            | 'nil'
            | 'this'
            | NUMBER
            | STRING
            | IDENTIFIER
            | '(' expression ')'
            | 'super' '.' IDENTIFIER ; // Añadimos super como primary

function    : IDENTIFIER '(' parameters? ')' block ;  // Definición de funciones
parameters  : IDENTIFIER ( ',' IDENTIFIER )* ;        // Lista de parámetros
arguments   : expression ( ',' expression )* ;        // Lista de argumentos

// Reglas léxicas
NUMBER      : DIGIT+ ( '.' DIGIT+ )? ;   // Manejo de números
STRING      : '"' (~["\\])* '"' ;        // Manejo de cadenas
IDENTIFIER  : ALPHA ( ALPHA | DIGIT )* ; // Manejo de identificadores
fragment ALPHA  : [a-zA-Z_] ;            // Letras
fragment DIGIT  : [0-9] ;                // Dígitos

// Ignorar espacios en blanco
WS          : [ \t\r\n]+ -> skip ;       // Ignorar espacios y saltos de línea