package pe.kydium.eclipserpg.gen;

/** Lo que el generador necesita saber del terreno (sin depender del servidor). */
public interface Terrain {
    /** Y del bloque sólido más alto (sin contar árboles ni plantas). */
    int ground(int x, int z);

    /** ¿Arriba hay agua, lava o hielo? */
    boolean water(int x, int z);

    /** id del bloque de arriba (grass_block, sand, snow_block…). */
    String surface(int x, int z);
}
