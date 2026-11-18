package br.com.infratrace.dao;

import br.com.infratrace.model.Ativo;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQL da tabela ativo. */
@Repository
public class AtivoDAO {

    private static final String COLUNAS = "SELECT atv_id, atv_nome, atv_fabricante, atv_modelo, atv_num_serie, "
            + "atv_patrimonio, atv_status, atv_data_aquisicao, atv_valor_compra, atv_garantia, atv_slot_inicio, "
            + "atv_slot_fim, atv_data_descarte, atv_motivo_descarte, atv_criado_em, fk_tpa_id, fk_rac_id, fk_nff_id "
            + "FROM ativo";

    /**
     * Lista com filtros opcionais. O WHERE é montado conforme os filtros recebidos, mas os
     * VALORES continuam indo por "?" (nunca concatenados no SQL), o que evita SQL injection.
     */
    public List<Ativo> listar(Connection con, String status, Integer rackId) throws SQLException {
        StringBuilder sql = new StringBuilder(COLUNAS).append(" WHERE 1 = 1");
        List<Object> parametros = new ArrayList<>();
        if (status != null) {
            sql.append(" AND atv_status = ?");
            parametros.add(status);
        }
        if (rackId != null) {
            sql.append(" AND fk_rac_id = ?");
            parametros.add(rackId);
        }
        sql.append(" ORDER BY atv_nome");
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            return listarResultado(ps);
        }
    }

    /** Ativos instalados em um rack, na ordem dos slots (usado no mapa de ocupação). */
    public List<Ativo> listarPorRack(Connection con, int rackId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE fk_rac_id = ? ORDER BY atv_slot_inicio")) {
            ps.setInt(1, rackId);
            return listarResultado(ps);
        }
    }

    /** Ativos não descartados cuja garantia vence entre as duas datas. */
    public List<Ativo> listarGarantiaVencendo(Connection con, LocalDate de, LocalDate ate) throws SQLException {
        String sql = COLUNAS + " WHERE atv_garantia BETWEEN ? AND ? AND atv_status <> 'DESCARTADO' ORDER BY atv_garantia";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, de);
            ps.setObject(2, ate);
            return listarResultado(ps);
        }
    }

    public Ativo buscarPorId(Connection con, int id) throws SQLException {
        return buscar(con, id, "");
    }

    /** Lê o ativo e trava a linha até o fim da transação (ver RackDAO.buscarPorIdParaAtualizar). */
    public Ativo buscarPorIdParaAtualizar(Connection con, int id) throws SQLException {
        return buscar(con, id, " FOR UPDATE");
    }

    private Ativo buscar(Connection con, int id, String trava) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COLUNAS + " WHERE atv_id = ?" + trava)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /**
     * Procura um ativo do rack cujo intervalo de slots se sobrepõe a [inicio, fim].
     * Dois intervalos se sobrepõem quando: inicioA <= fimB E fimA >= inicioB.
     * ignorarAtivoId serve para não comparar o ativo com ele mesmo numa movimentação.
     */
    public Ativo buscarConflitoDeSlots(Connection con, int rackId, int inicio, int fim, Integer ignorarAtivoId)
            throws SQLException {
        String sql = COLUNAS + " WHERE fk_rac_id = ? AND atv_slot_inicio <= ? AND atv_slot_fim >= ?"
                + " AND (? IS NULL OR atv_id <> ?) ORDER BY atv_slot_inicio LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, rackId);
            ps.setInt(2, fim);
            ps.setInt(3, inicio);
            ps.setObject(4, ignorarAtivoId);
            ps.setObject(5, ignorarAtivoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Maior slot ocupado no rack, ou null se o rack estiver vazio. */
    public Integer maiorSlotOcupado(Connection con, int rackId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT MAX(atv_slot_fim) FROM ativo WHERE fk_rac_id = ?")) {
            ps.setInt(1, rackId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, Integer.class);
            }
        }
    }

    public int inserir(Connection con, Ativo a) throws SQLException {
        String sql = "INSERT INTO ativo (atv_nome, atv_fabricante, atv_modelo, atv_num_serie, atv_patrimonio, "
                + "atv_status, atv_data_aquisicao, atv_valor_compra, atv_garantia, fk_tpa_id, fk_nff_id, "
                + "fk_rac_id, atv_slot_inicio, atv_slot_fim) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencherCadastro(ps, a);
            ps.setObject(12, a.getRackId());
            ps.setObject(13, a.getSlotInicio());
            ps.setObject(14, a.getSlotFim());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    /** Atualiza os dados cadastrais. A localização (rack e slots) só muda por movimentação. */
    public void atualizarCadastro(Connection con, Ativo a) throws SQLException {
        String sql = "UPDATE ativo SET atv_nome = ?, atv_fabricante = ?, atv_modelo = ?, atv_num_serie = ?, "
                + "atv_patrimonio = ?, atv_status = ?, atv_data_aquisicao = ?, atv_valor_compra = ?, "
                + "atv_garantia = ?, fk_tpa_id = ?, fk_nff_id = ? WHERE atv_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            preencherCadastro(ps, a);
            ps.setInt(12, a.getId());
            ps.executeUpdate();
        }
    }

    /** Passo 2 da movimentação: grava a nova posição (rack nulo = estoque). */
    public void atualizarLocalizacao(Connection con, int id, Integer rackId, Integer slotInicio, Integer slotFim)
            throws SQLException {
        String sql = "UPDATE ativo SET fk_rac_id = ?, atv_slot_inicio = ?, atv_slot_fim = ? WHERE atv_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, rackId);
            ps.setObject(2, slotInicio);
            ps.setObject(3, slotFim);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    public void atualizarStatus(Connection con, int id, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE ativo SET atv_status = ? WHERE atv_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** O CHECK ck_atv_descarte exige data, motivo e o ativo fora do rack. */
    public void registrarDescarte(Connection con, int id, LocalDate data, String motivo) throws SQLException {
        String sql = "UPDATE ativo SET atv_status = 'DESCARTADO', atv_data_descarte = ?, atv_motivo_descarte = ?, "
                + "fk_rac_id = NULL, atv_slot_inicio = NULL, atv_slot_fim = NULL WHERE atv_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, data);
            ps.setString(2, motivo);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public void excluir(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM ativo WHERE atv_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private void preencherCadastro(PreparedStatement ps, Ativo a) throws SQLException {
        ps.setString(1, a.getNome());
        ps.setString(2, a.getFabricante());
        ps.setString(3, a.getModelo());
        ps.setString(4, a.getNumSerie());
        ps.setString(5, a.getPatrimonio());
        ps.setString(6, a.getStatus());
        ps.setObject(7, a.getDataAquisicao());
        ps.setBigDecimal(8, a.getValorCompra());
        ps.setObject(9, a.getGarantia());
        ps.setInt(10, a.getTipoAtivoId());
        ps.setObject(11, a.getNotaFiscalId());
    }

    private List<Ativo> listarResultado(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Ativo> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    /** getObject(coluna, Classe) devolve null quando a coluna é NULL (getInt devolveria 0). */
    private Ativo mapear(ResultSet rs) throws SQLException {
        Ativo a = new Ativo();
        a.setId(rs.getInt("atv_id"));
        a.setNome(rs.getString("atv_nome"));
        a.setFabricante(rs.getString("atv_fabricante"));
        a.setModelo(rs.getString("atv_modelo"));
        a.setNumSerie(rs.getString("atv_num_serie"));
        a.setPatrimonio(rs.getString("atv_patrimonio"));
        a.setStatus(rs.getString("atv_status"));
        a.setDataAquisicao(rs.getObject("atv_data_aquisicao", LocalDate.class));
        a.setValorCompra(rs.getObject("atv_valor_compra", BigDecimal.class));
        a.setGarantia(rs.getObject("atv_garantia", LocalDate.class));
        a.setSlotInicio(rs.getObject("atv_slot_inicio", Integer.class));
        a.setSlotFim(rs.getObject("atv_slot_fim", Integer.class));
        a.setDataDescarte(rs.getObject("atv_data_descarte", LocalDate.class));
        a.setMotivoDescarte(rs.getString("atv_motivo_descarte"));
        a.setCriadoEm(rs.getObject("atv_criado_em", LocalDateTime.class));
        a.setTipoAtivoId(rs.getInt("fk_tpa_id"));
        a.setRackId(rs.getObject("fk_rac_id", Integer.class));
        a.setNotaFiscalId(rs.getObject("fk_nff_id", Integer.class));
        return a;
    }
}
