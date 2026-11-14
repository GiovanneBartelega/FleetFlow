import {test} from 'node:test';
import assert from 'node:assert/strict';
import {cents,decimal,monthDate} from '../src/finance.js';
import {money} from '../src/schemas.js';
import {imageMime} from '../src/routes/comprovantes.js';
import {makeFakePool,makeUser,startServer,tokenFor} from './helpers.js';
const ADMIN=makeUser({id:'12345678-1234-4234-8234-123456789012',perfil:'Administrador'});
const DRIVER=makeUser({id:'12345678-1234-4234-8234-123456789013',perfil:'OperadorMotorista'});
test('saldo exclui pendências do atual, usa centavos exatos e inclui previsão',async()=>{
 const pool=makeFakePool({users:[ADMIN],handlers:[sql=>sql.includes('GROUP BY c.Tipo,m.Status')?[[{tipo:'Entrada',status:'Pago',total:'100.10'},{tipo:'Saida',status:'Pago',total:'30.20'},{tipo:'Entrada',status:'Pendente',total:'50.00'},{tipo:'Saida',status:'Pendente',total:'90.00'}]]:undefined]});
 const api=await startServer({pool});try {
 const res=await api.request('GET','/api/movimentacoes/saldo?dataAte=2026-10-14',{token:tokenFor(ADMIN.Id)});
 assert.equal(res.status,200);assert.equal(res.body.saldoAtual,'69.90');assert.equal(res.body.saldoPrevisto,'29.90');
 assert.equal(res.body.aPagar,'90.00');assert.equal(res.body.aReceber,'50.00');
 }finally {await api.close();}
});
test('motorista não vê caixa nem envia comprovante',async()=>{
 const api=await startServer({pool:makeFakePool({users:[DRIVER]})});try {
 for(const [method,path] of [['GET','/api/movimentacoes/saldo'],['PUT','/api/movimentacoes/12345678-1234-4234-8234-123456789099/comprovante']])
  assert.equal((await api.request(method,path,{token:tokenFor(DRIVER.Id)})).status,403);
 }finally {await api.close();}
});
test('valores altos preservam centavos sem ponto flutuante',()=>{
 assert.equal(money.parse('999999999999.99'),'999999999999.99');
 assert.equal(decimal(cents('100.10')-cents('100.20')),'-0.10');
});
test('parcelas ancoradas no dia original com ajuste do fim de mês',()=>{
 assert.equal(monthDate('2026-01-31',1),'2026-02-28');
 assert.equal(monthDate('2026-01-31',2),'2026-03-31');
 assert.equal(monthDate('2028-01-31',1),'2028-02-29');
});
test('comprovante reconhece assinatura do arquivo e rejeita texto disfarçado',()=>{
 assert.equal(imageMime(Buffer.from('<script>alert(1)</script>')),null);
 assert.equal(imageMime(Buffer.from([255,216,255,224])),'image/jpeg');
 assert.equal(imageMime(Buffer.from([137,80,78,71,13,10,26,10])),'image/png');
});
test('falha ao gravar segunda parcela faz rollback e nunca commit',async()=>{
 let commits=0,rollbacks=0,released=0,inserts=0;
 const pool=makeFakePool({users:[ADMIN],handlers:[sql=>sql.startsWith('SELECT Id FROM categorias')?[[{Id:'c'}]]:undefined]});
 pool.getConnection=async()=>({beginTransaction:async()=>{},commit:async()=>{commits++},rollback:async()=>{rollbacks++},release(){released++},
  async query(sql){if(sql.includes('SELECT Id FROM formas_pagamento'))return [[{Id:'f'}]];
   if(sql.includes('INSERT INTO movimentacoes') && ++inserts===2)throw new Error('Falha simulada');return [{affectedRows:1}];}});
 const api=await startServer({pool});try {
 const response=await api.request('POST','/api/dividas',{token:tokenFor(ADMIN.Id),body:{descricao:'Financiamento',categoriaId:'12345678-1234-4234-8234-123456789099',formaPagamentoId:'12345678-1234-4234-8234-123456789098',quantidadeParcelas:3,valorParcela:'150.50',dataVencimentoPrimeira:'2026-01-31'}});
 assert.equal(response.status,500);assert.equal(commits,0);assert.equal(rollbacks,1);assert.equal(released,1);
 }finally {await api.close();}
});
