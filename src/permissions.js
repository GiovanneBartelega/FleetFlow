import { AppError } from './errors.js';

// Matriz de permissÃµes por mÃ³dulo e perfil.
// E = ver e editar Â· L = somente leitura Â· ausente = sem acesso
//
// Os mÃ³dulos que ainda nÃ£o possuem rotas completas permanecem na matriz
// para permitir que o aplicativo monte a navegaÃ§Ã£o dinÃ¢mica.
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

// CatÃ¡logo de itens do checklist: o motorista sÃ³ consulta.
itensChecklist: { Administrador: 'E', GestorFrota: 'E', OperadorMotorista: 'L' },

alertas: { Administrador: 'E', Financeiro: 'L', GestorFrota: 'E', OperadorMotorista: 'L' },
};

export function levelFor(perfil, modulo) {
return PERMISSION_MATRIX[modulo]?.[perfil] ?? null;
}

// Exemplo: { usuarios: 'E', categorias: 'L', movimentacoes: null, ... }
export function permissionsFor(perfil) {
return Object.fromEntries(
Object.keys(PERMISSION_MATRIX).map((modulo) => [modulo, levelFor(perfil, modulo)]),
);
}

// mode: 'read' aceita L ou E; 'write' exige E.
export function requireAccess(modulo, mode) {
return (req, res, next) => {
const level = levelFor(req.user?.perfil, modulo);
const allowed = mode === 'write'
? level === 'E'
: level === 'E' || level === 'L';

if (!allowed) {
  return next(
    new AppError(403, 'FORBIDDEN', 'Seu perfil nÃ£o tem permissÃ£o para esta aÃ§Ã£o.'),
  );
}

return next();

};
}

