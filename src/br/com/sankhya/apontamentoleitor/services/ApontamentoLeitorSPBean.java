package br.com.sankhya.apontamentoleitor.services;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.ejb.SessionBean;

import com.google.gson.JsonObject;
import com.sankhya.util.JsonUtils;

import br.com.sankhya.apontamentoleitor.utils.Apontamento;
import br.com.sankhya.apontamentoleitor.utils.MateriaPrima;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.core.JapeSession.SessionHandle;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.mgeprod.model.helper.ApontamentoHelper;
import br.com.sankhya.mgeprod.model.utils.ApontamentoTotem;
import br.com.sankhya.mgeprod.model.utils.ProdutoControle;
import br.com.sankhya.modelcore.util.BaseSPBean;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.ws.ServiceContext;

/**
 * @author Felipe Salles Lopes
 * @ejb.bean name="ApontamentoLeitorSP"
 *           jndi-name="br/com/sankhya/apontamentoleitor/services/ApontamentoLeitorSP"
 *           type="Stateless" transaction-type="Container" view-type="remote"
 * @ejb.transaction type="Supports" *
 * @ejb.util generate="false"
 */
public class ApontamentoLeitorSPBean extends BaseSPBean implements SessionBean {

	private static final long serialVersionUID = 1L;

	/**
	 * @throws Exception
	 * @ejb.interface-method tview-tipe="remote"
	 * @ejb.transaction type="Required"
	 */
	public void apontarProducao(ServiceContext ctx) throws Exception {

		final JsonObject requestBody = ctx.getJsonRequestBody();

		SessionHandle hnd = null;
		JdbcWrapper jdbc = null;

		try {
			hnd = JapeSession.open();
			EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
			jdbc = dwfEntityFacade.getJdbcWrapper();

			BigDecimal codbarras = JsonUtils.getBigDecimal(requestBody, "CODBARRAS");
			BigDecimal qtdApont = JsonUtils.getBigDecimal(requestBody, "QTDAPONT");
			Apontamento apontamento;

			// Se a variável qtdApont estiver nula, quer dizer que esse objeto não foi
			// passado na chamada do serviço.
			if (qtdApont == null)
				apontamento = getApontamentoInfo(jdbc, codbarras);
			else
				apontamento = getApontamentoInfo(jdbc, codbarras, qtdApont);

			// Se não achar resultado de acordo com o código de barras o serviço retornará
			// uma mensagem de aviso.
			if (apontamento.getIdiatv().equals(BigDecimal.ZERO)) {
				buildResponse(ctx, 0, "Código de barras não encontrado.");
				return;
			}
			// Se a operação de produção desse volume ainda não foi inicializada.
			if (apontamento.getDhinicio() == null) {
				buildResponse(ctx, 4, "É necessário inicializar a atividade da ordem de produção.");
				return;
			}

			// Se esse volume já foi apontado.
			if (apontamento.getNuapo() != null) {
				buildResponse(ctx, 3, "Esse volume já foi apontado.");
				return;
			}

			// Se o peso offline estiver liberado. (PopUp)
			if (apontamento.getPesoff().equals("S") && qtdApont == null) {
				buildResponse(ctx, 6, "Volume definido como peso offline.");
				return;
			}

			// Se o volume do produto acabado não for permitido para essa forma de
			// apontamento.
			if (!aceitaLeitor(jdbc, apontamento.getCodprodpa()) && apontamento.getPesoff().equals("N")) {
				buildResponse(ctx, 5, "Esse volume não é permitido para apontamento por leitor.");
				return;
			}

			// Se qtd. apontada for igual a 0, não faz o apontamento ainda. (PopUp)
			if (apontamento.getVolume().equals(BigDecimal.ZERO)) {
				buildResponse(ctx, 2, "Quantidade apontada não encontrada.");
				return;
			}

			ApontamentoHelper apontHelper = new ApontamentoHelper(jdbc);
			// Cria apontamento e retorna o nuapo.
			BigDecimal nuapo = apontHelper.criarApontamentoTotem(criarApontamento(jdbc, apontamento),
					apontamento.getCodusu(), null);

			apontamento.setNuapo(nuapo);

			// Marca o volume do apontamento de produção com o nro. único do apontamento
			// gerado.
			marcarApontamento(codbarras, apontamento);

			// Envia a resposta para o solicitante do serviço
			JsonObject resultObject = new JsonObject();
			resultObject.addProperty("IDIATV", apontamento.getIdiatv());
			resultObject.addProperty("NUAPO", nuapo);

			JsonObject statusObject = new JsonObject();
			statusObject.addProperty("statusResposta", 1);
			statusObject.add("valores", resultObject);

			JsonObject responseObject = new JsonObject();
			responseObject.add("response", statusObject);

			ctx.setJsonResponse(responseObject);

		} catch (SQLException s) {
			s.printStackTrace();
			throw new SQLException(s.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Causa: " + e.getCause());
			throw new Exception(e.getMessage());
		} finally {
			JapeSession.close(hnd);
		}

	}

	/**
	 * Cria o cabeçalho do apontamento que será gerado para a ordem de produção.
	 * 
	 * @param jdbc        conector do banco de dados.
	 * 
	 * @param apontamento instância que representa as informações do apontamento que
	 *                    está sendo criado.
	 * @param codbarras   o código identificador para o filtro do volume de ordem de
	 *                    produção.
	 * @return uma instância de ApontamentoTotem.
	 * @throws Exception intercepta uma exceção genérica.
	 */
	private ApontamentoTotem criarApontamento(JdbcWrapper jdbc, Apontamento apontamento) throws Exception {
		ApontamentoTotem apontamentoTotem = new ApontamentoTotem();
		apontamentoTotem.setIdiatv(apontamento.getIdiatv()); // Define o ID da atividade

		// Cria uma instância de um produto por lote.
		ProdutoControle produtoAcabado = new ProdutoControle(apontamento.getCodprodpa(), null);

		// Adiciona um produto acabado na instância de apontamento.
		apontamentoTotem.addProdutoAcabado(produtoAcabado, apontamento.getVolume(), BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.ZERO, null);

		// Busca uma lista de materiais utilizado no produtos acabados.
		apontamentoTotem = addMateriasPrimas(jdbc, apontamentoTotem, apontamento, produtoAcabado);

		return apontamentoTotem;
	}

	/**
	 * 
	 * @param jdbc
	 * @param codprodpa
	 * @return
	 * @throws Exception
	 */
	private boolean aceitaLeitor(JdbcWrapper jdbc, BigDecimal codprodpa) throws Exception {

		NativeSql sql = new NativeSql(jdbc);
		String aceitaLeitor = "N";

		sql.appendSql(" SELECT ");
		sql.appendSql("    COALESCE(VOL.AD_ACTAPOLEITOR, 'N') ");
		sql.appendSql(" FROM ");
		sql.appendSql("    TGFPRO PRO ");
		sql.appendSql("    JOIN TGFVOL VOL ON PRO.CODVOL = VOL.CODVOL ");
		sql.appendSql(" WHERE ");
		sql.appendSql("    PRO.CODPROD = :CODPRODPA");

		sql.setNamedParameter("CODPRODPA", codprodpa);

		ResultSet result = sql.executeQuery();

		if (result.next())
			aceitaLeitor = result.getString(1);

		result.close();
		return aceitaLeitor.equals("S");
	}

	/**
	 * Altera o campo NUAPO na tabela TPRAVO com o nro. único do apontamento
	 * recém-apontado.
	 * 
	 * @param codbarras Volume do produto a ser apontado.
	 * @param nuapo     Nro. único do apontamento
	 * 
	 */
	private void marcarApontamento(BigDecimal codbarras, Apontamento apontamento) throws Exception {
		SessionHandle hnd = null;
		/// JdbcWrapper jdbc = null;

		try {
			hnd = JapeSession.open();

			// Altera o valor do campo Nro. Apontamento para o NUAPO gerado.
			JapeFactory.dao("ApontamentoVolumes")
					.prepareToUpdateByPK(codbarras)
					.set("NUAPO", apontamento.getNuapo())
					.set("PESOBRUTO", apontamento.getVolume())
					.set("PESOLIQ", apontamento.getVolume())
					.update();

		} catch (Exception e) {
			throwExceptionRollingBack(e);
			e.printStackTrace();
			throw new Exception(e.getMessage());
		} finally {
			JapeSession.close(hnd);
		}
	}

	/**
	 * Busca as informações de cabeçalho de apontamento e cria uma instância de
	 * apontamento.
	 * 
	 * @param jdbc      conector do banco de dados.
	 * @param codbarras o código identificador para o filtro do volume de ordem de
	 *                  produção.
	 * @return Apontamento instância criada de apontamento.
	 * @throws SQLException intercepta uma exceção em caso de erro de consulta ou
	 *                      conexão com o banco de dados.
	 * @throws Exception    intercepta uma exceção genérica.
	 */
	private Apontamento getApontamentoInfo(JdbcWrapper jdbc, BigDecimal codbarras) throws SQLException, Exception {
		Apontamento apontamento = new Apontamento();
		NativeSql sql = new NativeSql(jdbc);
		// Consulta que retorna a quantidade a ser apontada.
		sql.appendSql(" SELECT ");
		sql.appendSql("    IATV.IDIATV, ");
		sql.appendSql("    IATV.CODEXEC, ");
		sql.appendSql("    STP_GET_CODUSULOGADO AS CODUSU, ");
		sql.appendSql("    IATV.DHINICIO, ");
		sql.appendSql("    IPA.CODPRODPA, ");
		sql.appendSql("    AVO.NUAPO, ");
		sql.appendSql("    COALESCE(PRO.AD_TAMVOL, 0) AS QTDAPONTAR, ");
		sql.appendSql("    COALESCE(IPROC.AD_VOLOP, 0) AS VOLAPONT, ");
		sql.appendSql("    COALESCE(IPROC.AD_PESOFF, 'N') AS PESOFF ");
		sql.appendSql(" FROM  ");
		sql.appendSql("    TPRIPROC IPROC ");
		sql.appendSql("    JOIN TPRAVO AVO   ON (IPROC.IDIPROC = AVO.IDIPROC) ");
		sql.appendSql("    JOIN TPRIATV IATV ON (IATV.IDIPROC = IPROC.IDIPROC ");
		sql.appendSql("                     AND AVO.IDIATV = IATV.IDIATV) ");
		sql.appendSql("    JOIN TPRIPA IPA   ON (IPROC.IDIPROC = IPA.IDIPROC) ");
		sql.appendSql("    JOIN TGFPRO PRO   ON (IPA.CODPRODPA = PRO.CODPROD) ");
		sql.appendSql(" WHERE ");
		sql.appendSql("    AVO.ID = :CODBARRAS");

		sql.setNamedParameter("CODBARRAS", codbarras);

		ResultSet result = sql.executeQuery();

		if (result.next()) {
			apontamento.setIdiatv(result.getBigDecimal("IDIATV"));
			apontamento.setQtdApont(result.getBigDecimal("QTDAPONTAR"));
			apontamento.setVolapont(result.getBigDecimal("VOLAPONT"));
			apontamento.setCodprodpa(result.getBigDecimal("CODPRODPA"));
			apontamento.setCodexec(result.getBigDecimal("CODEXEC"));
			apontamento.setCodusu(result.getBigDecimal("CODUSU"));
			apontamento.setNuapo(result.getBigDecimal("NUAPO"));
			apontamento.setDhinicio(result.getDate("DHINICIO"));
			apontamento.setPesoff(result.getString("PESOFF"));
			apontamento.setCodbarras(codbarras);
		}

		result.close();
		return apontamento;
	}

	/**
	 * Busca as informações de cabeçalho de apontamento, não incluso a quantidade
	 * apontada, e cria uma instância de apontamento.
	 * 
	 * @param jdbc      conector do banco de dados.
	 * @param codbarras o código identificador para o filtro do volume de ordem de
	 *                  produção.
	 * @param qtdApont  a quantidade apontada informada pelo usuário.
	 * @return instância criada de apontamento.
	 * @throws SQLException intercepta uma exceção em caso de erro de consulta ou
	 *                      conexão com o banco de dados.
	 * @throws Exception    intercepta uma exceção genérica.
	 */
	private Apontamento getApontamentoInfo(JdbcWrapper jdbc, BigDecimal codbarras, BigDecimal qtdApont)
			throws SQLException, Exception {
		Apontamento apontamento = new Apontamento();
		NativeSql sql = new NativeSql(jdbc);
		// Consulta que retorna a quantidade a ser apontada.
		sql.appendSql(" SELECT ");
		sql.appendSql("    IPA.CODPRODPA, ");
		sql.appendSql("    IATV.IDIATV, ");
		sql.appendSql("    IATV.CODEXEC, ");
		sql.appendSql("    STP_GET_CODUSULOGADO AS CODUSU, ");
		sql.appendSql("    IATV.DHINICIO, ");
		sql.appendSql("    AVO.ID AS CODBARRAS, ");
		sql.appendSql("    AVO.NUAPO, ");
		sql.appendSql("    COALESCE(IPROC.AD_PESOFF, 'N') AS PESOFF ");
		sql.appendSql(" FROM ");
		sql.appendSql("    TPRIPROC IPROC ");
		sql.appendSql("    JOIN TPRAVO AVO   ON (IPROC.IDIPROC = AVO.IDIPROC) ");
		sql.appendSql("    JOIN TPRIATV IATV ON (IATV.IDIPROC = IPROC.IDIPROC ");
		sql.appendSql("                     AND AVO.IDIATV = IATV.IDIATV) ");
		sql.appendSql("    JOIN TPRIPA IPA   ON (IPROC.IDIPROC = IPA.IDIPROC) ");
		sql.appendSql(" WHERE ");
		sql.appendSql("    AVO.ID = :CODBARRAS");

		sql.setNamedParameter("CODBARRAS", codbarras);

		ResultSet result = sql.executeQuery();

		if (result.next()) {
			apontamento.setIdiatv(result.getBigDecimal("IDIATV"));
			apontamento.setQtdApont(qtdApont);
			apontamento.setVolapont(qtdApont);
			apontamento.setCodprodpa(result.getBigDecimal("CODPRODPA"));
			apontamento.setCodexec(result.getBigDecimal("CODEXEC"));
			apontamento.setCodusu(result.getBigDecimal("CODUSU"));
			apontamento.setNuapo(result.getBigDecimal("NUAPO"));
			apontamento.setDhinicio(result.getDate("DHINICIO"));
			apontamento.setPesoff(result.getString("PESOFF"));
			apontamento.setCodbarras(codbarras);
		}

		result.close();
		return apontamento;
	}

	/**
	 * Esse método adiciona as matérias-primas de um produto acabado em um
	 * apontamento totem e o retorna.
	 * 
	 * Primeiramente o método irá alimentar as matérias-primas por uma consulta em
	 * uma lista de matérias-primas separadas por lote. Em seguida, ele criará um
	 * mapa de produtos no qual a chave é o código de produto de um produto e o
	 * valor é o saldo do mesmo. Então, o método irá percorrer a lista de
	 * matérias-primas.
	 * 
	 * Nas iterações, o método irá verificar se o produto da matéria-prima atual já
	 * foi adicionado no mapa de produtos e fazer as ações necessárias de acordo com
	 * a verificação. Além disso, também irá controlar a lógica de quantidade
	 * disponível e de saldo das matérias primas para, então, adicionar no
	 * apontamento de totem.
	 * 
	 * @param jdbc           conector do banco de dados.
	 * @param aptTotem       instância de um <cod>ApontamentoTotem</code> o qual é o
	 *                       responsável por passar as informações necessárias para
	 *                       o helper de apontamento.
	 * @param apontamento    instância de um <code>Apontamento</code> que possui as
	 *                       informações necessárias para o apontamento. de
	 *                       produção.
	 * @param produtoAcabado instância de um <code>ProdutoControle</code> que
	 *                       representa o produto acabado que está sendo apontado.
	 * @return ApontamentoTotem
	 * @throws Exception
	 */
	private ApontamentoTotem addMateriasPrimas(JdbcWrapper jdbc, ApontamentoTotem aptTotem, Apontamento apontamento,
			ProdutoControle produtoAcabado) throws Exception {
		
		ArrayList<BigDecimal> mpPrincipais = qtdMpPrincipal(jdbc, apontamento);
		Map<BigDecimal, BigDecimal> produtos = new HashMap<>();

		if (mpPrincipais.isEmpty())
			throw new Exception("Matéria Prima empenhada não encontrada na composição do Produto Acabado.");
		
		ArrayList<MateriaPrima> materiasPrimas = getProdutosMP(jdbc, apontamento, aptTotem);

		for (MateriaPrima mp : materiasPrimas) {
			BigDecimal qtdMp = mp.getQtdMP();
			BigDecimal qtdDisponivel = mp.getQtdDisponivel();
			BigDecimal saldo = null;

			System.out.println("\n=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
			System.out.println("Lista de Materias-primas");

			// Inserção no mapa de Produtos
			if (produtos.containsKey(mp.getMpPrincipal())) {
				System.out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
				System.out.println("Ja possui produto.");
				saldo = produtos.get(mp.getMpPrincipal());

			} else {
				System.out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
				System.out.println("Ainda nao possui produto.");
				saldo = mp.getQtdMP();
				produtos.put(mp.getMpPrincipal(), saldo);
			}
			
			System.out.println("Produto: " + mp.getCodprod());
			System.out.println("ProdMP: " + mp.getMpPrincipal());
			System.out.println("Lote: " + mp.getControle());
			System.out.println("Qtd. MP: " + qtdMp);
			System.out.println("Saldo: " + produtos.get(mp.getMpPrincipal()));
			System.out.println("Qtd. Disponivel: " + qtdDisponivel);

			// Consumo
			// Se QtdMP for menor ou igual do que a Qtd. Disponivel
			if (saldo.compareTo(qtdDisponivel) <= 0 && !saldo.equals(BigDecimal.ZERO)) {
				System.out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
				System.out.println("Tem estoque e eh diferente de 0");
				ProdutoControle pc = new ProdutoControle(mp.getCodprod(), mp.getControle());

				aptTotem.addMateriaPrima(produtoAcabado, pc, saldo); // Adiciona a materia-prima
				produtos.put(mp.getMpPrincipal(), BigDecimal.ZERO);
			} else if (saldo.compareTo(qtdDisponivel) == 1 && !saldo.equals(BigDecimal.ZERO)) { // Se QtdMP for maior do
																								// que a Qtd.
																								// Disponível.
				System.out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
				System.out.println("Nao tem estoque");
				ProdutoControle pc = new ProdutoControle(mp.getCodprod(), mp.getControle());

				aptTotem.addMateriaPrima(produtoAcabado, pc, qtdDisponivel); // Adiciona a materia-prima
				produtos.put(mp.getMpPrincipal(), saldo.subtract(qtdDisponivel));

			}

		} // for

		// Exibe erro de estoque insuficiente.
		for (Map.Entry<BigDecimal, BigDecimal> produto : produtos.entrySet())
			if (!produto.getValue().equals(BigDecimal.ZERO))
				throw new Exception("Estoque insuficiente para MP " + produto.getKey());

		return aptTotem;
	}

	/**
	 * Busca todas as matérias-primas que fazem parte do produto acabado a ser
	 * apontado.
	 * 
	 * @param jdbc        conector do banco de dados.
	 * @param apontamento instância de um <code>Apontamento</code> que possui as
	 *                    informações necessárias para o apontamento. de produção.
	 * 
	 * @param aptTotem    instância de um <cod>ApontamentoTotem</code> o qual é o
	 *                    responsável por passar as informações necessárias para o
	 *                    helper de apontamento.
	 * @return Uma lista de produtos de matéria prima que participam da produção de
	 *         um produto acabado.
	 * @throws Exception intercepta uma exceção genérica.
	 */
	private ArrayList<MateriaPrima> getProdutosMP(JdbcWrapper jdbc, Apontamento apontamento, ApontamentoTotem aptTotem)
			throws SQLException, Exception {

		NativeSql sql = new NativeSql(jdbc);

		// Consulta que retorna a quantidade a ser apontada.
		sql.appendSql(" SELECT ");
		sql.appendSql("    CODPRODMPPRIN, ");
		sql.appendSql("    CODPRODMP, ");
		sql.appendSql("    QTDMISTURA,");
		sql.appendSql("    CONTROLE, ");
		sql.appendSql("    QTDISPONIVEL ");
		sql.appendSql(" FROM ");
		sql.appendSql("    VW_SALDOS_MP_SKMS ");
		sql.appendSql(" WHERE ");
		sql.appendSql("    ID = :CODBARRAS ");
		sql.appendSql(" ORDER BY ");
		sql.appendSql("    CODPRODMP,");
		sql.appendSql("    NUNOTA, ");
		sql.appendSql("    SEQUENCIA ");

		sql.setNamedParameter("CODBARRAS", apontamento.getCodbarras());

		ResultSet result = null;
		ArrayList<MateriaPrima> materiasPrimas = new ArrayList<>();
		try {
			result = sql.executeQuery();

			// Percorre o conjunto de resultado da consulta executada e os adicionam no
			// mapa de matérias-prima.
			while (result.next()) {
				String controle = result.getString("CONTROLE");
				BigDecimal mpprin = result.getBigDecimal("CODPRODMPPRIN");
				BigDecimal qtdMistura = result.getBigDecimal("QTDMISTURA");
				BigDecimal qtdDisponivel = result.getBigDecimal("QTDISPONIVEL");
				BigDecimal codprodmp = result.getBigDecimal("CODPRODMP");

				BigDecimal qtdMp = apontamento.getVolume().multiply(qtdMistura);

				MateriaPrima materiaPrima = new MateriaPrima(codprodmp, controle, qtdMp, qtdDisponivel, mpprin);

				System.out.println("\n=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
				System.out.println("Obtendo materias-primas");
				System.out.println("Produto Principal: " + mpprin);
				System.out.println("Produto: " + codprodmp);
				System.out.println("Lote: " + controle);
				System.out.println("Qtd. Mistura: " + qtdMistura);
				System.out.println("Qtd. MP: " + qtdMp);
				System.out.println("Qtd. Disponivel: " + qtdDisponivel);

				// Já existe esse lote dessa materia-prima.
				if (materiasPrimas.contains(materiaPrima)) {
					System.out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
					System.out.println("Ja existe lote dessa materia-prima.");
					MateriaPrima mpExistente = materiasPrimas.get(materiasPrimas.indexOf(materiaPrima));
					materiaPrima.setQtdDisponivel(qtdDisponivel.add(mpExistente.getQtdDisponivel()));
					materiasPrimas.set(materiasPrimas.indexOf(materiaPrima), materiaPrima);
				} else {// Ainda nao existe essa materia-prima.
					System.out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
					System.out.println("Ainda nao existe lote dessa materia-prima.");
					materiasPrimas.add(materiaPrima);
				}
				
				//materiasPrimas.add(materiaPrima);

			} // while
		} catch (SQLException e) {
			e.printStackTrace();
			throw new SQLException("Erro na consulta de obtenção de matérias primas.\n" + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception("Erro na operação de matérias primas.\n" + e.getMessage());
		} finally {
			result.close();
		}

		return materiasPrimas;
	}

	/**
	 * Retorna a quantidade de MP principal de um apontamento de produção.
	 * 
	 * @param jdbc        driver de conexão com o banco de dados.
	 * @param apontamento instância de um <code>Apontamento</code> que possui as
	 *                    informações necessárias para o apontamento. de produção.
	 * @return <Object>ArrayList</Object> a quantidade de MP principal de um
	 *         apontamento de produção.
	 *         
	 */
	private ArrayList<BigDecimal> qtdMpPrincipal(JdbcWrapper jdbc, Apontamento apontamento) throws Exception {

		NativeSql sql = new NativeSql(jdbc);
		ArrayList<BigDecimal> mpPrincipal = new ArrayList<>();

		// Consulta que retorna a quantidade de MPs principais.
		sql.appendSql(" SELECT  ");
		sql.appendSql("    DISTINCT CODPRODMPPRIN ");
		sql.appendSql(" FROM ");
		sql.appendSql("    VW_SALDOS_MP_SKMS ");
		sql.appendSql(" WHERE ");
		sql.appendSql("    ID = :CODBARRAS ");

		sql.setNamedParameter("CODBARRAS", apontamento.getCodbarras());

		ResultSet result = sql.executeQuery();

		while (result.next()) 
			mpPrincipal.add(result.getBigDecimal(1));
		

		return mpPrincipal;
	}

	/**
	 * -- Respostas e Requisições JSON -- </br>
	 * Envia a resposta montada da requisição do serviço.
	 * 
	 * @param ctx      contexto atual do serviço.
	 * @param status   status da resposta do serviço.
	 * @param resposta valor da resposta do serviço.
	 */
	private void buildResponse(ServiceContext ctx, int status, Object resposta) {
		ctx.setJsonResponse(buildResponse(status, (String) resposta));
	}

	/**
	 * -- Respostas e Requisições JSON -- </br>
	 * Monta a resposta da requisição do serviço de acordo com o retorno da resposta
	 * do mesmo.
	 * 
	 * @param status   status da resposta do serviço.
	 * @param resposta valor da resposta do serviço.
	 * @return JsonObject objeto que representa a resposta do JSON criado.
	 */
	private JsonObject buildResponse(int status, String resposta) {
		JsonObject statusObject = new JsonObject();
		statusObject.addProperty("statusResposta", status);
		statusObject.addProperty("valores", resposta); // TODO: renomear resposta

		JsonObject responseObject = new JsonObject();
		responseObject.add("response", statusObject);

		return responseObject;
	}

}
