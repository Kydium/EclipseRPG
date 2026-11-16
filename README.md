# EclipseRPG

Plugin de Paper para la campaña **Crónicas del Eclipse**: niveles RPG, puntos de habilidad, Soles (☀), tiendas en los NPCs, contratos diarios, Grietas para farmear, Corrompidos, jefes con barra de vida y armas únicas.

- **Servidor:** Paper 26.2 (también debería funcionar en 26.3)
- **Java:** 25
- **Plugins que se usan con él:** Citizens (NPCs) y BetonQuest (misiones). EclipseRPG funciona sin ellos, pero las tiendas se abren desde los NPCs.

## Instalación

1. Copia `EclipseRPG-1.0.0.jar` en la carpeta `plugins/` del servidor junto con Citizens y BetonQuest.
2. Inicia el servidor una vez. Se crea `plugins/EclipseRPG/` con `config.yml`, `items.yml`, `mobs.yml`, `shops.yml` y `contracts.yml`.
3. Ve al lugar donde estará el Campamento del Alba y escribe `/eclipse setcampamento`.
4. Crea los NPCs (ver abajo).

## Comandos de los jugadores

| Comando | Qué hace |
| --- | --- |
| `/nivel` (o `/stats`) | Menú con tu nivel, XP, Soles y puntos para repartir |
| `/soles` | Ver tus Soles |
| `/pagar <jugador> <cantidad>` | Pasarle Soles a un amigo |

## Comandos de administración (`/eclipse`, solo OP)

| Comando | Qué hace |
| --- | --- |
| `/eclipse tienda <id> <jugador>` | Abre una tienda (`general`, `herreria`, `alquimia`, `mercado`) |
| `/eclipse contratos <jugador>` | Abre los contratos diarios |
| `/eclipse grieta <jugador>` | Abre una Grieta alrededor del jugador y sus amigos cercanos |
| `/eclipse xp <jugador> <n>` | Da XP RPG |
| `/eclipse soles <jugador> <dar\|quitar\|poner> <n>` | Maneja Soles |
| `/eclipse nivel <jugador> <n>` | Pone un nivel (para pruebas) |
| `/eclipse item <jugador> <id> [n]` | Da un objeto de `items.yml` |
| `/eclipse spawn <mob> <jugador> [n]` | Hace aparecer un Corrompido o jefe de `mobs.yml` cerca del jugador |
| `/eclipse info <jugador>` | Muestra el progreso del jugador |
| `/eclipse setcampamento` | Guarda el campamento (destino del Pergamino) |
| `/eclipse recargar` | Recarga todos los archivos `.yml` |

## NPCs con Citizens

Crea cada NPC, selecciónalo y ponle el comando. `<p>` se reemplaza por el nombre del jugador que hace clic.

```
/npc create Pip
/npc command add eclipse tienda general <p>

/npc create Bruno
/npc command add eclipse tienda herreria <p>

/npc create Fray_Odon
/npc command add eclipse tienda alquimia <p>

/npc create Capitana_Ines
/npc command add eclipse contratos <p>

/npc create El_Errante
/npc command add eclipse tienda mercado <p>
```

Para las Grietas puedes poner un segundo NPC junto a Inés (por ejemplo, "Portal de la Grieta") con `/npc command add eclipse grieta <p>`.

## Uso desde BetonQuest

- **Dar recompensas:** evento `command` que ejecute `eclipse xp %player% 250`, `eclipse soles %player% dar 100` o `eclipse item %player% hoja_del_alba`.
- **Hacer aparecer enemigos de misión:** `eclipse spawn zombi_corrompido %player% 5` o `eclipse spawn rondador_podrido %player%`.
- **Pedir un nivel mínimo:** condición `permission eclipserpg.nivel.10`. El plugin da el permiso `eclipserpg.nivel.N` por cada nivel alcanzado.
- **Saber el tier del jugador:** permiso `eclipserpg.tier.N` (1 a 10).

Los jefes entregan sus premios (Fragmento Solar y arma única) a cada jugador que esté a 32 bloques al morir. Las armas únicas se dan una sola vez por jugador; si repite el jefe recibe Soles.

## Cómo funciona

- **Niveles:** del 1 al 50. Para pasar del nivel N al N+1 se necesitan 150 × N de XP RPG. Cada nivel da 1 punto.
- **Habilidades:** Vida, Fuerza, Defensa, Agilidad y Fortuna. Se reparten en `/nivel` y se pueden reiniciar por 500 ☀.
- **Corrompidos naturales:** el 12% de los mobs hostiles que aparecen solos se vuelven Corrompidos con el tier del jugador más cercano (nivel 1-5 = tier 1, 6-10 = tier 2, etc.).
- **Contratos:** 3 por día y por jugador (hora de Lima). Si completas los 3, te dan un Fragmento de Mejora.
- **Grietas:** 5 oleadas de Corrompidos. La última trae un Guardián con 4 veces más vida. Si ganas: XP, Soles y 25% de probabilidad de un Fragmento de Mejora.
- **Mejora de armas:** Bruno sube +1 de daño a un arma única por 300 ☀ + 1 Fragmento de Mejora, hasta +5.

Todo se cambia en los `.yml` y se aplica con `/eclipse recargar`.

## Compilar

GitHub Actions compila el `.jar` en cada push a `main` y lo publica en **Releases**. Para compilar a mano:

```
gradle build                               # Paper 26.2
gradle build -PpaperVersion=26.3.build.+   # Paper 26.3
```

El `.jar` queda en `build/libs/`.
