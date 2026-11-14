package com.fleetflow.mobile.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

// Abre a foto do comprovante já reduzida e na posição certa.
//
// Câmera de celular costuma gravar a foto "deitada" e anotar no EXIF quanto ela deve
// girar para ficar em pé. O BitmapFactory ignora essa anotação e, ao comprimir de novo,
// o EXIF se perde: o comprovante chegava deitado na API. Aqui a rotação é aplicada nos
// pixels antes de comprimir. O ExifInterface do Android aceita InputStream desde a
// API 24, que é o minSdk do app.
object ImagemComprovante {

    // ladoMaximo: maior lado aceito, em pixels. A redução é em potências de 2, que é
    // rápida e não aloca a foto inteira na memória.
    fun carregar(context: Context, uri: Uri, ladoMaximo: Int): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

        var amostra = 1
        while (limites.outWidth / amostra > ladoMaximo || limites.outHeight / amostra > ladoMaximo) amostra *= 2

        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: return null

        val graus = grausDeRotacao(context, uri)
        if (graus == 0) return bitmap
        val matriz = Matrix().apply { postRotate(graus.toFloat()) }
        val girado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
        if (girado !== bitmap) bitmap.recycle()
        return girado
    }

    private fun grausDeRotacao(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { entrada ->
            when (ExifInterface(entrada).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)
}
