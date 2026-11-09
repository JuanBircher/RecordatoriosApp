package com.juanbircher.recordatorios.ui.navegacion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.juanbircher.recordatorios.ui.ajustes.AjustesRuta
import com.juanbircher.recordatorios.ui.crear.CrearRuta
import com.juanbircher.recordatorios.ui.detalle.DetalleRuta
import com.juanbircher.recordatorios.ui.listado.ListadoRuta

object Rutas {
    const val LISTADO = "listado"
    const val CREAR = "crear"
    const val AJUSTES = "ajustes"
    const val ARG_RECORDATORIO_ID = "recordatorioId"
    const val DETALLE = "detalle/{$ARG_RECORDATORIO_ID}"
    const val EDITAR = "editar/{$ARG_RECORDATORIO_ID}"

    fun detalle(id: Long) = "detalle/$id"
    fun editar(id: Long) = "editar/$id"
}

/**
 * Grafo de navegación. El NavController se queda en este nivel: las pantallas solo informan acciones
 * mediante lambdas, y acá se decide a dónde lleva cada una.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = Rutas.LISTADO, modifier = modifier) {
        composable(Rutas.LISTADO) {
            ListadoRuta(
                onCrearRecordatorio = { navController.siEstaActiva { navigate(Rutas.CREAR) } },
                onRecordatorioClick = { id -> navController.siEstaActiva { navigate(Rutas.detalle(id)) } },
                onAbrirAjustes = { navController.siEstaActiva { navigate(Rutas.AJUSTES) } }
            )
        }
        composable(Rutas.AJUSTES) {
            AjustesRuta(onVolver = { navController.siEstaActiva { popBackStack() } })
        }
        composable(
            route = Rutas.EDITAR,
            arguments = listOf(navArgument(Rutas.ARG_RECORDATORIO_ID) { type = NavType.LongType })
        ) {
            CrearRuta(
                onGuardado = { navController.popBackStack() },
                onCancelar = { navController.siEstaActiva { popBackStack() } }
            )
        }
        composable(Rutas.CREAR) {
            CrearRuta(
                onGuardado = { navController.popBackStack() },
                onCancelar = { navController.siEstaActiva { popBackStack() } }
            )
        }
        composable(
            route = Rutas.DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_RECORDATORIO_ID) { type = NavType.LongType })
        ) {
            DetalleRuta(
                onVolver = { navController.siEstaActiva { popBackStack() } },
                onEditar = { id -> navController.siEstaActiva { navigate(Rutas.editar(id)) } }
            )
        }
    }
}

/**
 * Ignora los toques mientras hay una transición en curso: un doble toque en "volver" sacaría también
 * al listado de la pila y dejaría la pantalla en blanco.
 */
private fun NavHostController.siEstaActiva(accion: NavHostController.() -> Unit) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) accion()
}
