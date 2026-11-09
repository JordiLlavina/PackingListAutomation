// El mensaje flotante de la maqueta («Marcada como hecha»). Va al <body> y no
// al árbol de React: tiene que seguir a la vista aunque la pantalla que lo
// lanza desaparezca justo después (al entrar, al volver a la lista).
export function toast(texto: string) {
  const aviso = document.createElement('div');
  aviso.className = 'toast';
  aviso.setAttribute('role', 'status');
  aviso.textContent = texto;
  document.body.appendChild(aviso);
  setTimeout(() => aviso.remove(), 2600);
}
