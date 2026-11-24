# Skills y plugins de Claude Code: qué hay en este PC y qué instalar en otro

Inventario tomado el 2026-11-24 del PC de casa (`~/.claude/`). Sirve para dejar
otro PC igual. Todo sale del marketplace oficial
`claude-plugins-official` (https://github.com/anthropics/claude-plugins-official).

## Qué hay instalado aquí

| Plugin | Qué aporta | Estado |
|---|---|---|
| `superpowers` | Skills de proceso: brainstorming, planes, TDD, depuración, revisión de código, worktrees | activo |
| `skill-creator` | Crear y mejorar skills | activo |
| `claude-md-management` | Auditar y actualizar `CLAUDE.md` | activo |
| `context7` | MCP con documentación al día de librerías | activo |
| `microsoft-docs` | MCP y skills de Microsoft Learn | activo |
| `playwright` | MCP para manejar un navegador | activo, pero **no conecta** (ver abajo) |
| `jdtls-lsp` | Servidor de lenguaje Java | activo, pero **sin binario** (ver abajo) |
| `typescript-lsp` | Servidor de lenguaje TypeScript | activo, pero **sin binario** (ver abajo) |
| `explanatory-output-style` | Estilo de respuesta explicativo | instalado y **desactivado**: no hace falta |

Además hay una skill suelta en `~/.claude/skills/frontend-design/`. Es idéntica
a la del plugin `frontend-design` del mismo marketplace, así que en el otro PC
se instala como plugin y no hay que copiar la carpeta.

## Qué instalar en el otro PC

1. **VS Code** con la extensión **Claude Code** (`anthropic.claude-code`) e
   iniciar sesión con la misma cuenta.
2. Dentro de Claude Code, uno por uno:

   ```
   /plugin marketplace add anthropics/claude-plugins-official
   /plugin install superpowers@claude-plugins-official
   /plugin install skill-creator@claude-plugins-official
   /plugin install claude-md-management@claude-plugins-official
   /plugin install context7@claude-plugins-official
   /plugin install microsoft-docs@claude-plugins-official
   /plugin install playwright@claude-plugins-official
   /plugin install jdtls-lsp@claude-plugins-official
   /plugin install typescript-lsp@claude-plugins-official
   /plugin install frontend-design@claude-plugins-official
   ```

3. Reiniciar Claude Code y comprobar con `/plugin` que salen los nueve activos.

## Lo que no hay que instalar

- **Las skills `anthropic-skills:*`** (docx, xlsx, pptx, pdf, docs,
  deep-research, google-workspace…) vienen de la cuenta de claude.ai y se
  sincronizan solas al iniciar sesión.
- **Las reglas del proyecto** (`CLAUDE.md`, `.claude/rules/`,
  `.claude/settings.json`) viajan con el repositorio: basta con clonarlo.

## Lo que aquí está a medias (y conviene dejar bien allí)

En este PC **no hay Node en el PATH** (el proyecto usa el suyo, en
`frontend/node/`) y por eso tres plugins están instalados pero no funcionan:

- `playwright` arranca con `npx` y falla al conectar.
- `typescript-lsp` necesita `npm install -g typescript-language-server typescript`.
- `jdtls-lsp` necesita un ejecutable `jdtls` en el PATH (Eclipse JDT.LS,
  instalación manual en Windows) y un JDK 17 o posterior.

Instalar **Node.js LTS** en el otro PC arregla los dos primeros.

## Lo que no viaja con el repositorio

- **La memoria de Claude** de este proyecto vive en
  `~/.claude/projects/c--Users-jordi-Documents-Workspace-Packing-List-Automation/memory/`.
  Si se quiere conservar, copiar esa carpeta a mano; el nombre de la carpeta
  del proyecto depende de la ruta donde se clone.
- **Los ajustes globales** de `~/.claude/settings.json`: modelo `fable`,
  esfuerzo `high`, permitidos `mvn`, `java`, `git status` y `git diff`, y
  denegados `rm -rf` y `git push --force`.

## Para trabajar en el proyecto

Lo que hay en este PC: JDK 17 (Eclipse Temurin 17.0.20), Maven 3.9.5 y Git
2.55. Node no hace falta para compilar: Maven baja el suyo a `frontend/node/`.
El resto (variables de entorno, comandos) está en `CLAUDE.md`.
