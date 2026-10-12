import { AppError } from './errors.js';

// Matriz de permissões por módulo e perfil.
//   E = ver e editar · L = somente leitura · ausente = sem acesso
//
// É a mesma matriz do "Roteiro de Teste de Permissões". Ela foi derivada do
// modelo de dados e precisa ser confirmada pelo grupo: se a regra mudar,
// basta ajustar esta tabela, nenhuma rota precisa ser alterada.
//
// Os módulos veiculos, rotas, viagens, manutencao, abastecimentos, checklist e
// alertas ainda não têm rotas nesta API; aparecem aqui para o app montar a
// navegação dinâmica a partir de GET /api/auth/me.
export const PERMISSION_MATRIX = {
  usuarios: { Administrador: 'E' },
  categorias: { Administrador: 'E', Financeiro: 'E', GestorFrota: 'L' },
  formasPagamento: { Administrador: 'E', Financeiro: 'E', GestorFrota: 'L' },
  movimentacoes: { Administrador: 'E', Financeiro: 'E' },
  dividas: { Administrador: 'E', Financeiro: 'E' },
  veiculos: { Administrador: 'E', Financeiro: 'L', GestorFrota: 'E', OperadorMotorista: 'L' },
  rotas: { Administrador: 'E', Financeiro: 'L', GestorFrota: 'E', OperadorMotorista: 'L' },
  viagens: { Administrador: 'E', Financeiro: 'L', GestorFrota: 'E', OperadorMotorista: 'E' },
  manutencao: { Administrador: 'E', Financeiro: 'L', GestorFrota: 'E' },
  abastecimentos: { Administrador: 'E', Financeiro: 'L', GestorFrota: 'E', OperadorMotorista: 'E' },
  checklist: { Administrador: 'E', GestorFrota: 'E', OperadorMotorista: 'E' },
  alertas: { Administrador: 'E', Financeiro: 'L', GestorFrota: 'E', OperadorMotorista: 'L' },
};

export function levelFor(perfil, modulo) {
  return PERMISSION_MATRIX[modulo]?.[perfil] ?? null;
}

// { usuarios: 'E', categorias: 'L', movimentacoes: null, ... }
export function permissionsFor(perfil) {
  return Object.fromEntries(
    Object.keys(PERMISSION_MATRIX).map((modulo) => [modulo, levelFor(perfil, modulo)]),
  );
}

// mode: 'read' aceita L ou E; 'write' exige E.
export function requireAccess(modulo, mode) {
  return (req, res, next) => {
    const level = levelFor(req.user?.perfil, modulo);
    const allowed = mode === 'write' ? level === 'E' : level === 'E' || level === 'L';
    if (!allowed) {
      return next(new AppError(403, 'FORBIDDEN', 'Seu perfil não tem permissão para esta ação.'));
    }
    return next();
  };
}
