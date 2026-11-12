// La administración (solo ADMIN, pestaña «Usuarios»), dos bloques apilados.
// «Usuarios»: la tabla con su rol, si está activo y su último acceso; por
// fila, cambiar el rol, desactivar o activar y cambiar la contraseña. Todos
// entran con usuario y contraseña; un usuario del taller de cuando se entraba
// con código sale marcado «sin contraseña» hasta que se le pone una.
// «Dispositivos»: los móviles y tablets del taller, que se dan de alta solos
// al entrar, con su último uso y «Revocar», que lo saca de la app en ese
// aparato. Cada acción vuelve a pedir las dos listas.
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { ErrorApi } from '../comun/api';
import {
  ROLES,
  cambiarUsuario,
  crearUsuario,
  dispositivos as pedirDispositivos,
  estadoDispositivo,
  revocarDispositivo,
  usuarios as pedirUsuarios,
  type Dispositivo,
  type Rol,
  type Usuario,
} from '../comun/admin';
import { Icono } from '../comun/iconos';
import { useSesion } from '../comun/sesion';
import { hace } from '../comun/tiempo';
import { toast } from '../comun/toast';

const TEXTO_ROL: Record<Rol, string> = { TALLER: 'Taller', DESPACHO: 'Despacho', ADMIN: 'Admin' };

/** Lo que cabe de un nombre de navegador en la tabla; el entero va en el `title`. */
const LARGO_NOMBRE = 60;

function mensaje(e: unknown, porDefecto: string): string {
  return e instanceof ErrorApi ? e.message : porDefecto;
}

function recortar(texto: string): string {
  return texto.length > LARGO_NOMBRE ? `${texto.slice(0, LARGO_NOMBRE - 1)}…` : texto;
}

const NUEVO_VACIO = { nombre: '', login: '', password: '', rol: 'TALLER' as Rol };

export default function Admin() {
  const { sesion } = useSesion();
  const [listaUsuarios, setListaUsuarios] = useState<Usuario[] | null>(null);
  const [listaDispositivos, setListaDispositivos] = useState<Dispositivo[] | null>(null);
  const [errorUsuarios, setErrorUsuarios] = useState<string | null>(null);
  const [errorDispositivos, setErrorDispositivos] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  const dialogoAlta = useRef<HTMLDialogElement>(null);
  const [nuevo, setNuevo] = useState(NUEVO_VACIO);
  const [errorAlta, setErrorAlta] = useState<string | null>(null);

  const dialogoPassword = useRef<HTMLDialogElement>(null);
  const [paraPassword, setParaPassword] = useState<Usuario | null>(null);
  const [password, setPassword] = useState('');
  const [errorPassword, setErrorPassword] = useState<string | null>(null);

  const dialogoRevocar = useRef<HTMLDialogElement>(null);
  const [aRevocar, setARevocar] = useState<Dispositivo | null>(null);

  const cargar = useCallback(async () => {
    await Promise.all([
      pedirUsuarios()
        .then((u) => {
          setListaUsuarios(u);
          setErrorUsuarios(null);
        })
        .catch((e: unknown) => setErrorUsuarios(mensaje(e, 'No se han podido cargar los usuarios'))),
      pedirDispositivos()
        .then((d) => {
          setListaDispositivos(d);
          setErrorDispositivos(null);
        })
        .catch((e: unknown) => setErrorDispositivos(mensaje(e, 'No se han podido cargar los dispositivos'))),
    ]);
  }, []);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  const cambiar = async (u: Usuario, cambios: Parameters<typeof cambiarUsuario>[1], hecho: string) => {
    setOcupado(true);
    try {
      await cambiarUsuario(u.id, cambios);
      toast(hecho);
      await cargar();
    } catch (e) {
      toast(mensaje(e, 'No se ha podido guardar el cambio'));
    } finally {
      setOcupado(false);
    }
  };

  const abrirAlta = () => {
    setNuevo(NUEVO_VACIO);
    setErrorAlta(null);
    dialogoAlta.current?.showModal();
  };

  const darDeAlta = async (e: FormEvent) => {
    e.preventDefault();
    setOcupado(true);
    setErrorAlta(null);
    try {
      await crearUsuario({ ...nuevo, nombre: nuevo.nombre.trim(), login: nuevo.login.trim() });
      dialogoAlta.current?.close();
      toast(`${nuevo.nombre.trim()} dado de alta`);
      await cargar();
    } catch (err) {
      setErrorAlta(mensaje(err, 'No se ha podido dar de alta el usuario'));
    } finally {
      setOcupado(false);
    }
  };

  const abrirPassword = (u: Usuario) => {
    setParaPassword(u);
    setPassword('');
    setErrorPassword(null);
    dialogoPassword.current?.showModal();
  };

  const guardarPassword = async (e: FormEvent) => {
    e.preventDefault();
    if (!paraPassword) return;
    setOcupado(true);
    setErrorPassword(null);
    try {
      await cambiarUsuario(paraPassword.id, { password });
      dialogoPassword.current?.close();
      toast('Contraseña cambiada');
      await cargar();
    } catch (err) {
      setErrorPassword(mensaje(err, 'No se ha podido cambiar la contraseña'));
    } finally {
      setOcupado(false);
    }
  };

  const abrirRevocar = (d: Dispositivo) => {
    setARevocar(d);
    dialogoRevocar.current?.showModal();
  };

  const revocar = async () => {
    if (!aRevocar) return;
    setOcupado(true);
    try {
      await revocarDispositivo(aRevocar.id);
      dialogoRevocar.current?.close();
      toast('Dispositivo revocado');
      await cargar();
    } catch (err) {
      dialogoRevocar.current?.close();
      toast(mensaje(err, 'No se ha podido revocar el dispositivo'));
    } finally {
      setOcupado(false);
    }
  };

  return (
    <div className="admin">
      <section aria-labelledby="titulo-usuarios">
        <div className="fila-titulo">
          <h2 id="titulo-usuarios">Usuarios</h2>
          <button className="boton marca" type="button" onClick={abrirAlta}>
            <Icono nombre="mas" /> Dar de alta un usuario
          </button>
        </div>
        {errorUsuarios ? (
          <p className="error">{errorUsuarios}</p>
        ) : !listaUsuarios ? (
          <p className="ayuda">Cargando…</p>
        ) : (
          <div className="marco-tabla">
            <table className="tabla-admin">
              <thead>
                <tr>
                  <th scope="col">Nombre</th>
                  <th scope="col">Login</th>
                  <th scope="col">Rol</th>
                  <th scope="col">Activo</th>
                  <th scope="col">Último acceso</th>
                  <th scope="col">
                    <span className="solo-lector">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {listaUsuarios.map((u) => {
                  const soyYo = u.id === sesion?.id;
                  return (
                    <tr key={u.id} className={u.activo ? undefined : 'apagado'}>
                      <td>
                        <b>{u.nombre}</b>
                      </td>
                      <td>
                        {u.login}
                        {!u.conPassword && (
                          <span className="sin-password" title="No puede entrar hasta que se le ponga una contraseña">
                            sin contraseña
                          </span>
                        )}
                      </td>
                      <td>
                        <select
                          aria-label={`Rol de ${u.nombre}`}
                          value={u.rol}
                          disabled={ocupado || soyYo}
                          title={soyYo ? 'No puedes cambiarte el rol a ti mismo' : undefined}
                          onChange={(e) => void cambiar(u, { rol: e.target.value as Rol }, `${u.nombre} pasa a ${TEXTO_ROL[e.target.value as Rol]}`)}
                        >
                          {ROLES.map((r) => (
                            <option key={r} value={r}>
                              {TEXTO_ROL[r]}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>{u.activo ? 'sí' : 'no'}</td>
                      <td>{u.ultimoAcceso ? hace(u.ultimoAcceso) : 'nunca'}</td>
                      <td>
                        <div className="acciones-fila">
                          <button
                            className="boton"
                            type="button"
                            disabled={ocupado || (soyYo && u.activo)}
                            title={soyYo && u.activo ? 'No puedes desactivarte a ti mismo' : undefined}
                            onClick={() => void cambiar(u, { activo: !u.activo }, u.activo ? `${u.nombre} desactivado` : `${u.nombre} activado`)}
                          >
                            {u.activo ? 'Desactivar' : 'Activar'}
                          </button>
                          <button className={u.conPassword ? 'boton' : 'boton marca'} type="button" disabled={ocupado} onClick={() => abrirPassword(u)}>
                            {u.conPassword ? 'Cambiar contraseña' : 'Poner contraseña'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section aria-labelledby="titulo-dispositivos">
        <div className="fila-titulo">
          <h2 id="titulo-dispositivos">Dispositivos</h2>
        </div>
        {errorDispositivos ? (
          <p className="error">{errorDispositivos}</p>
        ) : !listaDispositivos ? (
          <p className="ayuda">Cargando…</p>
        ) : listaDispositivos.length === 0 ? (
          <p className="ayuda">Todavía no ha entrado nadie del taller</p>
        ) : (
          <div className="marco-tabla">
            <table className="tabla-admin">
              <thead>
                <tr>
                  <th scope="col">Usuario</th>
                  <th scope="col">Navegador</th>
                  <th scope="col">Alta</th>
                  <th scope="col">Último uso</th>
                  <th scope="col">Estado</th>
                  <th scope="col">
                    <span className="solo-lector">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {listaDispositivos.map((d) => {
                  const estado = estadoDispositivo(d);
                  const nombre = d.nombre ?? '';
                  return (
                    <tr key={d.id} className={d.revocado ? 'apagado' : undefined}>
                      <td>
                        <b>{d.usuario ?? `Usuario ${d.usuarioId}`}</b>
                      </td>
                      <td title={nombre.length > LARGO_NOMBRE ? nombre : undefined}>{recortar(nombre) || '—'}</td>
                      <td>{hace(d.alta)}</td>
                      <td>{d.ultimoUso ? hace(d.ultimoUso) : 'sin usar'}</td>
                      <td>
                        <span className="estado-dispositivo" data-estado={estado}>
                          {estado}
                        </span>
                      </td>
                      <td>
                        {!d.revocado && (
                          <div className="acciones-fila">
                            <button className="boton peligro" type="button" disabled={ocupado} onClick={() => abrirRevocar(d)}>
                              Revocar
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <dialog ref={dialogoAlta} className="dialogo" aria-labelledby="titulo-alta">
        <form onSubmit={(e) => void darDeAlta(e)}>
          <h2 id="titulo-alta">Dar de alta un usuario</h2>
          <label className="campo">
            Nombre
            <input value={nuevo.nombre} maxLength={120} autoFocus onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })} />
          </label>
          <label className="campo">
            Login
            <input value={nuevo.login} maxLength={80} autoComplete="off" onChange={(e) => setNuevo({ ...nuevo, login: e.target.value })} />
          </label>
          <label className="campo">
            Contraseña
            <input
              type="password"
              value={nuevo.password}
              autoComplete="new-password"
              onChange={(e) => setNuevo({ ...nuevo, password: e.target.value })}
            />
          </label>
          {nuevo.rol === 'TALLER' && (
            <p className="ayuda">Con ella entra en la tablet y en su móvil; cada aparato se queda dentro y sale abajo, en «Dispositivos».</p>
          )}
          <label className="campo">
            Rol
            <select value={nuevo.rol} onChange={(e) => setNuevo({ ...nuevo, rol: e.target.value as Rol })}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {TEXTO_ROL[r]}
                </option>
              ))}
            </select>
          </label>
          {errorAlta && <p className="error">{errorAlta}</p>}
          <div className="botones">
            <button className="boton ok" type="submit" disabled={ocupado || !nuevo.nombre.trim() || !nuevo.login.trim() || !nuevo.password.trim()}>
              Dar de alta
            </button>
            <button className="boton" type="button" onClick={() => dialogoAlta.current?.close()}>
              Cancelar
            </button>
          </div>
        </form>
      </dialog>

      <dialog ref={dialogoPassword} className="dialogo" aria-labelledby="titulo-password">
        <form onSubmit={(e) => void guardarPassword(e)}>
          <h2 id="titulo-password">{paraPassword?.conPassword ? 'Cambiar' : 'Poner'} la contraseña de {paraPassword?.nombre}</h2>
          <label className="campo">
            Contraseña nueva
            <input type="password" value={password} autoComplete="new-password" autoFocus onChange={(e) => setPassword(e.target.value)} />
          </label>
          {errorPassword && <p className="error">{errorPassword}</p>}
          <div className="botones">
            <button className="boton ok" type="submit" disabled={ocupado || !password.trim()}>
              Cambiar contraseña
            </button>
            <button className="boton" type="button" onClick={() => dialogoPassword.current?.close()}>
              Cancelar
            </button>
          </div>
        </form>
      </dialog>

      <dialog ref={dialogoRevocar} className="dialogo" aria-labelledby="titulo-revocar">
        <div className="formulario">
          <h2 id="titulo-revocar">Revocar el dispositivo de {aRevocar?.usuario ?? 'este usuario'}</h2>
          <p>Ese aparato sale de la app y, para volver a usarlo, tendrá que entrar con su contraseña. ¿Revocar?</p>
          <div className="botones">
            <button className="boton peligro" type="button" disabled={ocupado} onClick={() => void revocar()}>
              Revocar
            </button>
            <button className="boton" type="button" onClick={() => dialogoRevocar.current?.close()}>
              Cancelar
            </button>
          </div>
        </div>
      </dialog>
    </div>
  );
}
