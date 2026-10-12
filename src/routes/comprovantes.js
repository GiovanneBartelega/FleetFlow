import express, { Router } from 'express';
import { AppError, asyncHandler } from '../errors.js';
import { requireAccess } from '../permissions.js';
import { uuid } from '../schemas.js';
export function imageMime(bytes) {
  if (bytes.length >= 8 && bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) return 'image/png';
  if (bytes.length >= 3 && bytes[0]===255 && bytes[1]===216 && bytes[2]===255) return 'image/jpeg';
  if (bytes.length >= 12 && bytes.toString('ascii',0,4)==='RIFF' && bytes.toString('ascii',8,12)==='WEBP') return 'image/webp';
  return null;
}
export function comprovantesRouter({pool}) {
  const router=Router();
  async function exists(id) {
    const [rows]=await pool.query('SELECT Id FROM movimentacoes WHERE Id=? AND Ativo=1',[id]);
    if (!rows.length) throw new AppError(404,'NOT_FOUND','Movimentação não encontrada.');
  }
  router.put('/:id/comprovante',requireAccess('movimentacoes','write'),
    express.raw({type:['image/jpeg','image/png','image/webp'],limit:'5mb'}),asyncHandler(async(req,res)=>{
      const id=uuid.parse(req.params.id);
      await exists(id);
      if (!Buffer.isBuffer(req.body) || !imageMime(req.body)) throw new AppError(400,'INVALID_FILE','Envie uma imagem JPEG, PNG ou WebP como corpo da requisição.');
      const mime=imageMime(req.body);
      await pool.query(`INSERT INTO comprovantes(MovimentacaoId,MimeType,Conteudo) VALUES(?,?,?)
        ON DUPLICATE KEY UPDATE MimeType=VALUES(MimeType),Conteudo=VALUES(Conteudo),CriadoEm=UTC_TIMESTAMP(3)`,[id,mime,req.body]);
      res.json({movimentacaoId:id,mimeType:mime,tamanho:req.body.length,url:`/api/movimentacoes/${id}/comprovante`});
    }));
  router.get('/:id/comprovante',requireAccess('movimentacoes','read'),asyncHandler(async(req,res)=>{
    const id=uuid.parse(req.params.id); await exists(id);
    const [rows]=await pool.query('SELECT MimeType,Conteudo FROM comprovantes WHERE MovimentacaoId=?',[id]);
    if (!rows.length) throw new AppError(404,'NOT_FOUND','Comprovante não encontrado.');
    res.set('Cache-Control','private, no-store').type(rows[0].MimeType).send(rows[0].Conteudo);
  }));
  router.delete('/:id/comprovante',requireAccess('movimentacoes','write'),asyncHandler(async(req,res)=>{
    const id=uuid.parse(req.params.id); await exists(id);
    await pool.query('DELETE FROM comprovantes WHERE MovimentacaoId=?',[id]);
    res.status(204).end();
  }));
  return router;
}
