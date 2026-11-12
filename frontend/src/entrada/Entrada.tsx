// La entrada a la app, para quien todavía no ha entrado: usuario y contraseña,
// igual para todos (noviembre de 2026; antes el taller tecleaba un código de
// alta). Lo que se abre lo decide el rol: el taller ve su cara y su aparato
// se queda dado de alta, así que en la tablet y en el móvil solo se entra una
// vez; el despacho entra para la jornada.
import { useState, type FormEvent } from 'react';
import { ErrorApi } from '../comun/api';
import { LogoPuntotres } from '../comun/LogoPuntotres';
import { useSesion } from '../comun/sesion';

function mensajeDe(error: unknown): string {
  return error instanceof ErrorApi ? error.message : 'No se ha podido entrar';
}

export default function Entrada() {
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
    <>
      <header className="cabecera">
        <LogoPuntotres className="logo" />
        <h1>Modelos · Puntotres</h1>
      </header>
      <main>
        <form className="entrada" onSubmit={enviar}>
          <h2 className="saludo">Entrar</h2>
          <label className="campo">
            Usuario
            <input
              name="login"
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              required
              value={login}
              onChange={(e) => setLogin(e.target.value)}
            />
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
        </form>
      </main>
    </>
  );
}
