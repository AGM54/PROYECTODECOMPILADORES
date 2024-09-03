
# Compilador e IDE de CompiScript

## Introducción
Este proyecto es un compilador e IDE para el lenguaje de programación CompiScript. Incluye análisis léxico, parsing, análisis semántico y generación de código. El IDE proporciona un entorno fácil de usar para escribir, depurar y ejecutar código en CompiScript.

## Requisitos Previos

- **Java Development Kit (JDK)**: Asegúrate de tener instalado JDK. Puedes descargarlo desde [el sitio oficial de Oracle](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html).
- **IntelliJ IDEA**: Este proyecto está configurado para ejecutarse en IntelliJ IDEA. Puedes descargarlo desde [JetBrains](https://www.jetbrains.com/idea/download/).
- **ANTLR v4**: Usamos ANTLR v4 para generar el lexer y el parser a partir de la gramática de CompiScript.

## Configuración del Proyecto

1. **Clonar el Repositorio:**
    ```bash
    git clone https://github.com/AGM54/PROYECTODECOMPILADORES.git
    cd compiscript
    ```

2. **Abrir el Proyecto en IntelliJ IDEA:**
   - Abre IntelliJ IDEA.
   - Selecciona `Abrir` desde el menú `Archivo`.
   - Navega al repositorio clonado y selecciónalo.

3. **Instalar el Plugin de ANTLR en IntelliJ IDEA:**
   - Ve a `Archivo > Configuraciones > Plugins`.
   - Busca `ANTLR v4` y haz clic en `Instalar`.
   - Reinicia IntelliJ IDEA después de la instalación.

4. **Configurar ANTLR en el Proyecto:**
   - Una vez instalado el plugin de ANTLR, navega al directorio `src/main/antlr4`.
   - Haz clic derecho en tu archivo de gramática `.g4` y selecciona `Configurar ANTLR...`.
   - Configura los ajustes de ANTLR .
5. **Generar Lexer y Parser:**
   - Abre tu archivo de gramática `.g4`.
   - Presiona `Ctrl + Shift + G` (Windows/Linux) o `Cmd + Shift + G` (Mac) para generar el lexer y el parser a partir de la gramática.

6. **Construir el Proyecto:**
   - Ve a `Construir > Construir Proyecto` o presiona `Ctrl + F9` para compilar el proyecto.
   - Asegúrate de que el proyecto se compile sin errores.

## Ejecutar el Compilador

1. **Ejecutar la Clase Principal:**
   - Ve al directorio `src/main/java`.
   - Ubica la clase `Main`
   - Haz clic derecho en ella y selecciona `Ejecutar 'Main'`.

2. **Usar el IDE:**
   - El IDE te permite escribir, editar y ejecutar código en CompiScript de manera interactiva.
   - Utiliza el editor proporcionado para escribir tus programas en CompiScript.
   - Haz clic en el botón `Ejecutar` para ejecutar el código y ver los resultados en la consola de salida.

## Información Adicional

- **Archivo de Gramática ANTLR:** El archivo de gramática para CompiScript se encuentra en `src/main/antlr4`.
- **Archivos Generados:** Los archivos de lexer y parser se generan en el directorio `src/main/java` bajo el paquete especificado.

Para más instrucciones detalladas y solución de problemas, consulta la documentación oficial de IntelliJ IDEA y la documentación de ANTLR v4.

---




