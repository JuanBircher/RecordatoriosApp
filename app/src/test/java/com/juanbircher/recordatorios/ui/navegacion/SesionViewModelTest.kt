package com.juanbircher.recordatorios.ui.navegacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.juanbircher.recordatorios.ui.AuthFalso
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SesionViewModelTest {

    /** ViewModel de prueba que anota si se liberó. */
    class Espia : ViewModel() {
        var liberado = false
        public override fun onCleared() {
            liberado = true
        }
    }

    private val vm = SesionViewModel(AuthFalso())

    private fun espiaEn(uid: String): Espia {
        val dueno = object : ViewModelStoreOwner {
            override val viewModelStore = vm.almacenDe(uid)
        }
        return ViewModelProvider(dueno, viewModelFactory { initializer { Espia() } })[Espia::class.java]
    }

    @Test
    fun `la misma cuenta reutiliza su almacen, por ejemplo al rotar la pantalla`() {
        assertSame(vm.almacenDe("uid-ana"), vm.almacenDe("uid-ana"))
    }

    @Test
    fun `al cambiar de cuenta se liberan los ViewModel de la anterior`() {
        val deAna = espiaEn("uid-ana")

        vm.liberarOtras(uidActual = "uid-beto")

        assertTrue("sus corrutinas y observaciones se cancelan", deAna.liberado)
        assertNotSame(deAna, espiaEn("uid-ana"))
    }

    @Test
    fun `los ViewModel de la cuenta activa no se tocan`() {
        val deAna = espiaEn("uid-ana")

        vm.liberarOtras(uidActual = "uid-ana")

        assertFalse(deAna.liberado)
    }

    @Test
    fun `al cerrar sesion se libera todo`() {
        val deAna = espiaEn("uid-ana")

        vm.liberarOtras(uidActual = null)

        assertTrue(deAna.liberado)
    }
}
