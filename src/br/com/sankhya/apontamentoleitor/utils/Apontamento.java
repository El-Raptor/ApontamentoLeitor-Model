package br.com.sankhya.apontamentoleitor.utils;

import java.math.BigDecimal;
import java.sql.Date;

public class Apontamento {
	private BigDecimal nuapo;
	private BigDecimal idiatv;
	private BigDecimal qtdApont;
	private BigDecimal codprodpa;
	private BigDecimal volapont;
	private BigDecimal codexec;
	private BigDecimal codbarras;
	private BigDecimal codusu;
	private String pesoff;
	private Date dhinicio;
	
	public Apontamento() {
		this.nuapo = BigDecimal.ZERO;
		this.idiatv = BigDecimal.ZERO;
		this.qtdApont = BigDecimal.ZERO;
		this.codprodpa = BigDecimal.ZERO;
		this.volapont = BigDecimal.ZERO;
		this.codexec = BigDecimal.ZERO;
	}

	public BigDecimal getNuapo() {
		return nuapo;
	}

	public void setNuapo(BigDecimal nuapo) {
		this.nuapo = nuapo;
	}

	public BigDecimal getIdiatv() {
		return idiatv;
	}

	public void setIdiatv(BigDecimal idiatv) {
		this.idiatv = idiatv;
	}

	public BigDecimal getQtdApont() {
		return qtdApont;
	}

	public void setQtdApont(BigDecimal qtdApont) {
		this.qtdApont = qtdApont;
	}

	public BigDecimal getCodprodpa() {
		return codprodpa;
	}

	public void setCodprodpa(BigDecimal codprodpa) {
		this.codprodpa = codprodpa;
	}

	public BigDecimal getVolapont() {
		return volapont;
	}

	public void setVolapont(BigDecimal volapont) {
		this.volapont = volapont;
	}

	public BigDecimal getCodexec() {
		return codexec;
	}

	public void setCodexec(BigDecimal codexec) {
		this.codexec = codexec;
	}	

	public Date getDhinicio() {
		return dhinicio;
	}

	public void setDhinicio(Date dhinicio) {
		this.dhinicio = dhinicio;
	}

	public String getPesoff() {
		return pesoff;
	}

	public void setPesoff(String pesoff) {
		this.pesoff = pesoff;
	}

	public BigDecimal getCodbarras() {
		return codbarras;
	}

	public void setCodbarras(BigDecimal codbarras) {
		this.codbarras = codbarras;
	}

	public BigDecimal getVolume() {
		return volapont.equals(BigDecimal.ZERO) ? qtdApont : volapont;
	}

	public BigDecimal getCodusu() {
		return codusu;
	}

	public void setCodusu(BigDecimal codusu) {
		this.codusu = codusu;
	}
	
	
}
