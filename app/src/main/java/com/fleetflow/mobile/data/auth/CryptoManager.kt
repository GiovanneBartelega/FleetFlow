package com.fleetflow.mobile.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Criptografa os tokens antes de eles irem para o DataStore (a especificação pede
// armazenamento criptografado).
//
// A chave fica no Android Keystore: ela é criada dentro do aparelho e nunca sai
// de lá. Nem o próprio app consegue ler a chave; ele só pede ao Keystore para
// cifrar e decifrar. Assim, quem copiar o arquivo do DataStore leva só texto
// embaralhado.
object CryptoManager {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "fleetflow_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12     // tamanho do vetor de inicialização do GCM
    private const val TAG_BITS = 128    // tamanho da etiqueta que detecta adulteração

    // Pega a chave do Keystore; na primeira vez, cria.
    @Synchronized
    private fun chave(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gerador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gerador.generateKey()
    }

    // Texto -> texto cifrado em Base64, com o vetor de inicialização na frente.
    // O vetor não é segredo, mas é necessário para decifrar.
    fun criptografar(texto: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, chave())
        val cifrado = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cifrado, Base64.NO_WRAP)
    }

    // Faz o caminho de volta. Se algo não bater (chave apagada, valor antigo
    // gravado sem criptografia, arquivo adulterado), devolve null.
    fun descriptografar(base64: String): String? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val cifrado = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(cifrado), Charsets.UTF_8)
    }.getOrNull()
}