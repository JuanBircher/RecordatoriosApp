// Pruebas de las reglas de seguridad de Firestore contra el emulador (npm test).
// Cada prueba usa la identidad que tendría la app: una cuenta autenticada, otra cuenta o nadie.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
} from 'firebase/firestore';

const ANA = 'uid-ana';
const BETO = 'uid-beto';

let entorno;

/** Lo mismo que escribe la app (MapeosFirestore.kt + actualizadoEl del servidor). */
function recordatorio(cambios = {}) {
  return {
    titulo: 'Entregar la actividad 4',
    categoriaId: 'estudio',
    prioridad: 'ALTA',
    estado: 'PENDIENTE',
    creadoEl: '2026-11-13',
    actualizadoEl: serverTimestamp(),
    ...cambios,
  };
}

const ruta = (db, uid, clave = 'clave-1') => doc(db, 'users', uid, 'reminders', clave);
const comoAna = () => entorno.authenticatedContext(ANA).firestore();
const comoBeto = () => entorno.authenticatedContext(BETO).firestore();
const sinSesion = () => entorno.unauthenticatedContext().firestore();

before(async () => {
  entorno = await initializeTestEnvironment({
    projectId: 'demo-recordatorios',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

beforeEach(async () => {
  await entorno.clearFirestore();
  // Un recordatorio de Ana ya guardado, escrito sin reglas (como si viniera de su teléfono).
  await entorno.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(ruta(ctx.firestore(), ANA, 'existente'), recordatorio({ actualizadoEl: new Date() }));
  });
});

after(async () => {
  await entorno?.cleanup();
});

describe('la dueña de la cuenta', () => {
  test('crea, lee, lista, modifica y borra sus recordatorios', async () => {
    const db = comoAna();
    await assertSucceeds(setDoc(ruta(db, ANA, 'nuevo'), recordatorio()));
    await assertSucceeds(getDoc(ruta(db, ANA, 'nuevo')));
    await assertSucceeds(getDocs(collection(db, 'users', ANA, 'reminders')));
    await assertSucceeds(setDoc(ruta(db, ANA, 'nuevo'), recordatorio({ estado: 'COMPLETADO', completadoEl: '2026-11-14' })));
    await assertSucceeds(deleteDoc(ruta(db, ANA, 'nuevo')));
  });

  test('reenviar la misma escritura reemplaza el documento (reintento sin duplicar)', async () => {
    const db = comoAna();
    await assertSucceeds(setDoc(ruta(db, ANA, 'reintento'), recordatorio()));
    await assertSucceeds(setDoc(ruta(db, ANA, 'reintento'), recordatorio()));
    const lista = await getDocs(collection(db, 'users', ANA, 'reminders'));
    const claves = lista.docs.map((d) => d.id).sort();
    if (JSON.stringify(claves) !== JSON.stringify(['existente', 'reintento'])) {
      throw new Error(`se esperaban 2 documentos y hay: ${claves}`);
    }
  });
});

describe('otra cuenta', () => {
  test('no puede leer un recordatorio ajeno', async () => {
    await assertFails(getDoc(ruta(comoBeto(), ANA, 'existente')));
  });

  test('no puede listar la colección ajena', async () => {
    await assertFails(getDocs(collection(comoBeto(), 'users', ANA, 'reminders')));
  });

  test('no puede crear ni modificar en la colección ajena', async () => {
    await assertFails(setDoc(ruta(comoBeto(), ANA, 'intruso'), recordatorio()));
    await assertFails(setDoc(ruta(comoBeto(), ANA, 'existente'), recordatorio({ titulo: 'Modificado por Beto' })));
  });

  test('no puede borrar un recordatorio ajeno', async () => {
    await assertFails(deleteDoc(ruta(comoBeto(), ANA, 'existente')));
  });

  test('sí puede usar su propia colección', async () => {
    await assertSucceeds(setDoc(ruta(comoBeto(), BETO, 'propio'), recordatorio()));
  });
});

describe('sin sesión', () => {
  test('no puede leer ni escribir nada', async () => {
    await assertFails(getDoc(ruta(sinSesion(), ANA, 'existente')));
    await assertFails(setDoc(ruta(sinSesion(), ANA, 'anonimo'), recordatorio()));
  });
});

describe('validación del documento en el servidor', () => {
  test('rechaza campos que no existen en el modelo', async () => {
    await assertFails(setDoc(ruta(comoAna(), ANA, 'extra'), recordatorio({ esAdmin: true })));
  });

  test('rechaza un documento sin campos obligatorios', async () => {
    const { titulo, ...sinTitulo } = recordatorio();
    await assertFails(setDoc(ruta(comoAna(), ANA, 'sin-titulo'), sinTitulo));
  });

  test('rechaza valores fuera de los permitidos', async () => {
    await assertFails(setDoc(ruta(comoAna(), ANA, 'p'), recordatorio({ prioridad: 'URGENTE' })));
    await assertFails(setDoc(ruta(comoAna(), ANA, 'e'), recordatorio({ estado: 'ARCHIVADO' })));
    await assertFails(setDoc(ruta(comoAna(), ANA, 't'), recordatorio({ titulo: 'x'.repeat(101) })));
    await assertFails(setDoc(ruta(comoAna(), ANA, 'f'), recordatorio({ creadoEl: 'ayer' })));
  });

  test('rechaza una fecha de modificación inventada por el cliente', async () => {
    await assertFails(setDoc(ruta(comoAna(), ANA, 'fecha'), recordatorio({ actualizadoEl: new Date('2020-01-01') })));
  });

  test('fuera de users/{uid}/reminders no se permite nada', async () => {
    await assertFails(setDoc(doc(comoAna(), 'users', ANA), { nombre: 'Ana' }));
    await assertFails(getDocs(collection(comoAna(), 'users')));
  });
});
