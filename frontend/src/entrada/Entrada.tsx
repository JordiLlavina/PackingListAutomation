// La entrada a la app, para quien todavía no ha entrado. No se intenta
// adivinar si el aparato es un PC o una tablet: sale el login de despacho con
// un enlace «Soy del taller», que lleva al teclado del código de alta. El
// aparato que lo elige lo recuerda y la próxima vez abre directamente en el
// teclado (la tablet del taller solo vuelve aquí si le quitan el alta).
import { useState, type FormEvent } from 'react';
import { ErrorApi } from '../comun/api';
import { useSesion } from '../comun/sesion';
import { toast } from '../comun/toast';

const RECUERDO = 'modelos-entrada';
const CIFRAS = 6;
const TECLAS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '←', '0', 'OK'];

type Modo = 'login' | 'taller';

function modoRecordado(): Modo {
  try {
    return localStorage.getItem(RECUERDO) === 'taller' ? 'taller' : 'login';
  } catch {
    return 'login';
  }
}

function recordar(modo: Modo) {
  try {
    localStorage.setItem(RECUERDO, modo);
  } catch {
    // Sin almacenamiento (ventana privada): la próxima vez saldrá el login.
  }
}

function mensajeDe(error: unknown): string {
  return error instanceof ErrorApi ? error.message : 'No se ha podido entrar';
}

export default function Entrada() {
  const [modo, setModo] = useState<Modo>(modoRecordado);

  const cambiar = (nuevo: Modo) => {
    recordar(nuevo);
    setModo(nuevo);
  };

  return (
    <>
      <header className="cabecera">
        <h1>Modelos · Puntotres</h1>
      </header>
      <main>{modo === 'taller' ? <Teclado onLogin={() => cambiar('login')} /> : <Login onTaller={() => cambiar('taller')} />}</main>
    </>
  );
}

function Login({ onTaller }: { onTaller: () => void }) {
  const { entrar } = useSesion();
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault();
    setEnviando(true);
    setError('');
    try {
      await entrar(login.trim(), password);
    } catch (e) {
      setError(mensajeDe(e));
      setEnviando(false);
    }
  };

  return (
    <form className="entrada" onSubmit={enviar}>
      <h2 className="saludo">Entrar</h2>
      <label className="campo">
        Usuario
        <input name="login" autoComplete="username" autoCapitalize="none" required value={login} onChange={(e) => setLogin(e.target.value)} />
      </label>
      <label className="campo">
        Contraseña
        <input
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      <button className="boton marca" type="submit" disabled={enviando}>
        Entrar
      </button>
      <p className="error" role="alert">
        {error}
      </p>
      <button className="enlace" type="button" onClick={onTaller}>
        Soy del taller
      </button>
    </form>
  );
}

function Teclado({ onLogin }: { onLogin: () => void }) {
  const { entrarConCodigo } = useSesion();
  const [codigo, setCodigo] = useState('');
  const [enviando, setEnviando] = useState(false);

  const tecla = async (t: string) => {
    if (enviando) return;
    if (t === '←') {
      setCodigo(codigo.slice(0, -1));
    } else if (t === 'OK') {
      if (codigo.length < CIFRAS) {
        toast('Faltan cifras');
        return;
      }
      setEnviando(true);
      try {
        await entrarConCodigo(codigo, navigator.userAgent.slice(0, 80));
        toast('Tablet registrada');
      } catch (e) {
        // Un código que no vale, caducado, o demasiados intentos: lo dice el servidor.
        toast(mensajeDe(e));
        setCodigo('');
        setEnviando(false);
      }
    } else if (codigo.length < CIFRAS) {
      setCodigo(codigo + t);
    }
  };

  return (
    <>
      <h2 className="saludo">Soy del taller</h2>
      <p className="sub">Pide a despacho el código de 6 cifras y tecléalo aquí. Solo hace falta la primera vez.</p>
      <div className="codigo" aria-live="polite">
        {codigo.padEnd(CIFRAS, '·')}
      </div>
      <div className="teclado">
        {TECLAS.map((t) => (
          <button key={t} type="button" onClick={() => void tecla(t)} disabled={enviando && t === 'OK'} aria-label={t === '←' ? 'Borrar' : undefined}>
            {t}
          </button>
        ))}
      </div>
      <button className="enlace" type="button" onClick={onLogin}>
        Entrar con usuario y contraseña
      </button>
    </>
  );
}
