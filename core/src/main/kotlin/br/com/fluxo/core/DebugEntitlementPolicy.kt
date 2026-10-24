package br.com.fluxo.core

/** Regra do "Depuração: Pro": só existe fora de build publicado; no publicado nunca aparece nem vale. */
object DebugEntitlementPolicy {
    fun switchVisible(published: Boolean): Boolean = !published

    /** [forced] vem de estado possivelmente persistido; no publicado é ignorado. */
    fun effectivePro(published: Boolean, real: Boolean, forced: Boolean?): Boolean =
        if (published) real else forced ?: real
}
