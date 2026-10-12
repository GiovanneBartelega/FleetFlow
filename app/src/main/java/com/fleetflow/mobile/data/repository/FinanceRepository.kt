package com.fleetflow.mobile.data.repository

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import com.fleetflow.mobile.data.api.*
import com.fleetflow.mobile.data.model.*
import com.fleetflow.mobile.ui.screens.*
import com.google.gson.Gson
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

object FinanceRepository {
    private var context:Context?=null
    private lateinit var api:FinanceApi
    val categorias=mutableStateListOf<CategoriaDto>()
    val formas=mutableStateListOf<FormaDto>()
    var saldo by mutableStateOf<SaldoDto?>(null); private set
    var totais by mutableStateOf<TotaisDto?>(null); private set
    var totalPages by mutableStateOf(1); private set
    fun init(ctx:Context) {context=ctx.applicationContext;api=ApiClient.getRetrofit(ctx).create(FinanceApi::class.java)}
    private fun <T> checked(response:Response<T>):T {
        if(!response.isSuccessful) {
            val error=runCatching {Gson().fromJson(response.errorBody()?.string(),ErrorResponseDto::class.java)}.getOrNull()
            throw ApiException(error?.error?.code,error?.error?.message?:"Erro ${response.code()}")
        }
        return response.body()?:throw Exception("Resposta vazia")
    }
    private fun checkEmpty(response:Response<*>) {if(!response.isSuccessful) checked(response)}
    suspend fun catalogs() {categorias.clear();categorias.addAll(checked(api.categorias()).data);formas.clear();formas.addAll(checked(api.formas()).data)}
    suspend fun load(page:Int=1,busca:String="",status:String="",tipo:String="") {
        val q=mutableMapOf("page" to page.toString(),"pageSize" to "20")
        if(busca.isNotBlank())q["busca"]=busca
        if(status.isNotBlank())q["status"]=status
        if(tipo.isNotBlank())q["tipo"]=tipo
        val result=checked(api.movimentacoes(q));MovimentacaoRepo.itens.clear();MovimentacaoRepo.itens.addAll(result.data.map {it.toModel()})
        totalPages=result.totalPages;totais=result.totais;saldo=checked(api.saldo())
    }
    suspend fun refreshSaldo() {saldo=checked(api.saldo())}
    suspend fun save(m:Movimentacao):String {
        val cat=categorias.firstOrNull {it.titulo==m.categoria}?:throw Exception("Selecione uma categoria")
        val forma=formas.firstOrNull {it.nome==m.formaPagamento}?:throw Exception("Selecione uma forma")
        val body=MovRequestDto(BigDecimal.valueOf(m.valorCentavos,2).toPlainString(),m.descricao,cat.id,forma.id,
            if(m.status==StatusMov.PAGO)"Pago" else "Pendente",isoDate(m.dataVencimento),if(m.status==StatusMov.PAGO)isoDate(m.dataPagamento) else null,m.syncVersion)
        val row=checked(if(m.syncVersion==null)api.criar(body) else api.editar(m.id,body))
        return row.id
    }
    suspend fun upload(id:String,uri:Uri) {
        val ctx=context?:throw Exception("Aplicativo não iniciado")
        val bytes=withContext(Dispatchers.IO) {
            // Ler dimensões antes de decodificar: evita alocar uma foto inteira de alta resolução.
            val opts=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            ctx.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,opts)}
            if(opts.outWidth<=0)throw Exception("Imagem inválida")
            var sample=1
            while(opts.outWidth/sample>1600 || opts.outHeight/sample>1600) sample*=2
            val bitmap=ctx.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})}
                ?:throw Exception("Não foi possível abrir a imagem")
            ByteArrayOutputStream().use {stream->bitmap.compress(Bitmap.CompressFormat.JPEG,80,stream);bitmap.recycle();stream.toByteArray()}
        }
        checked(api.comprovante(id,bytes.toRequestBody("image/jpeg".toMediaType())))
    }
    suspend fun pay(m:Movimentacao) {checked(api.pagar(m.id,mapOf("dataPagamento" to java.time.LocalDate.now().toString())))}
    suspend fun delete(m:Movimentacao) {checkEmpty(api.remover(m.id))}
    suspend fun createCat(nome:String,tipo:String) {checked(api.criarCategoria(mapOf("titulo" to nome,"tipo" to tipo)));catalogs()}
    suspend fun editCat(id:String,nome:String) {checked(api.editarCategoria(id,mapOf("titulo" to nome)));catalogs()}
    suspend fun deleteCat(id:String) {checkEmpty(api.removerCategoria(id));catalogs()}
    suspend fun createForma(nome:String) {checked(api.criarForma(mapOf("nome" to nome)));catalogs()}
    suspend fun editForma(id:String,nome:String) {checked(api.editarForma(id,mapOf("nome" to nome)));catalogs()}
    suspend fun deleteForma(id:String) {checkEmpty(api.removerForma(id));catalogs()}
    suspend fun createDivida(body:DividaRequestDto) {checked(api.divida(body))}
    fun clear() {categorias.clear();formas.clear();saldo=null;totais=null;MovimentacaoRepo.itens.clear()}
}
fun isoDate(text:String?):String? {
    if(text.isNullOrBlank())return null
    val format=SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).apply {isLenient=false}
    val position=java.text.ParsePosition(0)
    val date=format.parse(text,position)?:throw IllegalArgumentException("Data inválida. Use dd/MM/aaaa")
    if(position.index!=text.length)throw IllegalArgumentException("Data inválida. Use dd/MM/aaaa")
    return SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(date)
}
private fun displayDate(iso:String?):String = iso?.split("-")?.let { if(it.size==3) "${it[2]}/${it[1]}/${it[0]}" else "" }?:""
fun MovDto.toModel()=Movimentacao(id=id,descricao=descricao,valorCentavos=BigDecimal(valor).movePointRight(2).longValueExact(),
    tipo=if(categoria.tipo=="Entrada")Tipo.ENTRADA else Tipo.SAIDA,categoria=categoria.titulo,formaPagamento=formaPagamento.nome,
    data=displayDate(dataPagamento?:dataVencimento),status=if(status=="Pago")StatusMov.PAGO else StatusMov.PENDENTE,
    dataVencimento=displayDate(dataVencimento),dataPagamento=displayDate(dataPagamento),syncVersion=syncVersion)
