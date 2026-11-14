/** Executar apenas em banco de teste inicializado, sem usuários. Não usa Google real. */
import assert from 'node:assert/strict';
import {createPool} from '../src/db.js';
import {loadConfig} from '../src/config.js';
import {createApp} from '../src/app.js';
import {readFileSync} from 'node:fs';
const config=loadConfig({...process.env,NODE_ENV:'test',DEV_LOGIN:'true',JWT_SECRET:'integration-test-only-'.repeat(3)});
const pool=createPool(config.db);
const [[initial]]=await pool.query('SELECT COUNT(*) AS total FROM usuarios');
if(Number(initial.total)!==0)throw new Error('Use banco vazio de teste. O smoke cria contas e dados fictícios.');
let google={sub:'smoke-google-admin',email:'smoke.admin@fleetflow.com',nome:'Smoke Admin',emailVerified:true,foto:null};
const app=createApp({pool,config,verifyGoogleToken:async()=>google,logger:{error(error){if(error?.message!=="Falha proposital na segunda parcela")console.error(error)}}});
const server=await new Promise(resolve=>{const s=app.listen(0,'127.0.0.1',()=>resolve(s))});
const base=`http://127.0.0.1:${server.address().port}`;
let token;let checks=0;
async function call(method,path,body,status=200,options={}) {
 const res=await fetch(base+path,{method,headers:{...(token?{Authorization:`Bearer ${token}`}:{'x-noauth':'1'}),...(body?{'Content-Type':options.mime??'application/json'}:{}),...options.headers},body:body?(options.mime?body:JSON.stringify(body)):undefined});
 const text=await res.text();let value;try{value=JSON.parse(text)}catch{value=text}
 assert.equal(res.status,status,`${method} ${path}: ${text}`);checks++;return value;
}
try {
 await call('GET','/health');
 const first=await call('POST','/api/auth/google',{idToken:'fake-token-for-integration-test'});
 assert.equal(first.usuario.perfil.nome,'Administrador');assert.equal(first.usuario.status,'Ativo');token=first.token;
 google={...google,sub:'smoke-second',email:'smoke.second@fleetflow.com'};
 await call('POST','/api/auth/google',{idToken:'fake-token-for-integration-test'},403);
 const pending=(await call('GET','/api/usuarios')).data.find(u=>u.email===google.email);
 await call('PATCH',`/api/usuarios/${pending.id}`,{status:'Ativo'});
 const next=await call('POST','/api/auth/refresh',{refreshToken:first.refreshToken});
 await call('POST','/api/auth/refresh',{refreshToken:first.refreshToken},401);token=next.token;
 const cat=await call('POST','/api/categorias',{titulo:'Smoke Entrada',tipo:'Entrada'},201);
 const out=await call('POST','/api/categorias',{titulo:'Smoke Saida',tipo:'Saida'},201);
 const forma=await call('POST','/api/formas-pagamento',{nome:'Smoke método'},201);
 const catEdit=await call('PATCH',`/api/categorias/${cat.id}`,{titulo:'Smoke Receita',syncVersion:cat.syncVersion});
 assert.ok(catEdit.syncVersion>cat.syncVersion);
 await call('PATCH',`/api/categorias/${cat.id}`,{titulo:'Conflito',syncVersion:cat.syncVersion},409);
 const m=await call('POST','/api/movimentacoes',{valor:'100.10',descricao:'Smoke receita',categoriaId:cat.id,formaPagamentoId:forma.id,status:'Pago',dataPagamento:'2026-10-01'},201);
 await call('POST','/api/movimentacoes',{valor:'30.20',descricao:'Smoke despesa',categoriaId:out.id,formaPagamentoId:forma.id,status:'Pago',dataPagamento:'2026-10-02'},201);
 const pendingMov=await call('POST','/api/movimentacoes',{valor:'15.30',descricao:'Smoke pendente',categoriaId:out.id,formaPagamentoId:forma.id,dataVencimento:'2026-10-14'},201);
 const saldo=await call('GET','/api/movimentacoes/saldo?dataAte=2026-10-14');
 assert.equal(saldo.saldoAtual,'69.90');assert.equal(saldo.saldoPrevisto,'54.60');
 const page=await call('GET','/api/movimentacoes?pageSize=1&tipo=Saida');assert.equal(page.total,2);assert.equal(page.data.length,1);assert.equal(page.totais.saidas,'45.50');
 await call('POST',`/api/movimentacoes/${pendingMov.id}/pagar`,{dataPagamento:'2026-10-10'});
 await call('POST',`/api/movimentacoes/${pendingMov.id}/pagar`,{dataPagamento:'2026-10-10'},409);
 const image=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0u8AAAAASUVORK5CYII=','base64');
 await call('PUT',`/api/movimentacoes/${m.id}/comprovante`,image,200,{mime:'image/png'});
 const receipt=await fetch(base+`/api/movimentacoes/${m.id}/comprovante`,{headers:{Authorization:`Bearer ${token}`}});assert.equal(receipt.status,200);assert.deepEqual(Buffer.from(await receipt.arrayBuffer()),image);checks++;
 await call('PUT',`/api/movimentacoes/${m.id}/comprovante`,Buffer.from('bad'),400,{mime:'image/png'});
 await call('PUT',`/api/movimentacoes/${m.id}/comprovante`,Buffer.alloc(5*1024*1024+1),413,{mime:'image/png'});
 await call('DELETE',`/api/movimentacoes/${m.id}/comprovante`,undefined,204);
 await call('GET',`/api/movimentacoes/${m.id}/comprovante`,undefined,404);
 const debt=await call('POST','/api/dividas',{descricao:'Smoke dívida',categoriaId:out.id,formaPagamentoId:forma.id,quantidadeParcelas:3,valorParcela:'9.99',dataVencimentoPrimeira:'2026-01-31'},201);
 const parcels=await call('GET',`/api/movimentacoes?dividaId=${debt.id}`);assert.equal(parcels.total,3);assert.equal(parcels.totais.saidas,'29.97');assert.ok(parcels.data.some(p=>p.dataVencimento==='2026-02-28'));
 // Falha real na segunda parcela: verificar que dívida e primeira parcela não ficaram no MySQL.
 const [[beforeDebts]]=await pool.query('SELECT COUNT(*) AS total FROM dividas');
 const [[beforeMov]]=await pool.query('SELECT COUNT(*) AS total FROM movimentacoes');
 const getConnection=pool.getConnection.bind(pool);
 pool.getConnection=async()=>{
   const conn=await getConnection();const query=conn.query.bind(conn);let insertCount=0;
   conn.query=async(sql,params)=>{if(sql.includes('INSERT INTO movimentacoes') && ++insertCount===2)throw new Error('Falha proposital na segunda parcela');return query(sql,params);};
   const release=conn.release.bind(conn);conn.release=()=>{conn.query=query;release();};return conn;
 };
 await call('POST','/api/dividas',{descricao:'Smoke rollback',categoriaId:out.id,formaPagamentoId:forma.id,quantidadeParcelas:3,valorParcela:'8.00',dataVencimentoPrimeira:'2026-10-14'},500);
 pool.getConnection=getConnection;
 const [[afterDebts]]=await pool.query('SELECT COUNT(*) AS total FROM dividas');
 const [[afterMov]]=await pool.query('SELECT COUNT(*) AS total FROM movimentacoes');
 assert.equal(afterDebts.total,beforeDebts.total);assert.equal(afterMov.total,beforeMov.total);checks+=2;
 await call('DELETE',`/api/dividas/${debt.id}`,undefined,409);
 await call('DELETE',`/api/movimentacoes/${m.id}`,undefined,204);
 await call('GET',`/api/movimentacoes/${m.id}`,undefined,404);
 await call('DELETE',`/api/categorias/${cat.id}`,undefined,204);
 await call('GET','/openapi.json');
 const docs=await fetch(base+'/docs/');assert.equal(docs.status,200);checks++;
 await call('POST','/api/auth/logout',{refreshToken:next.refreshToken},204);
 await call('POST','/api/auth/refresh',{refreshToken:next.refreshToken},401);
 console.log(`MySQL ${checks} verificações de integração passaram: bootstrap, aprovação, refresh, CRUD, versão, filtros, saldo, upload e parcelas.`);
} finally {await new Promise(resolve=>server.close(resolve));await pool.end();}
