package br.com.sankhya.apontamentoleitor.utils;

import java.math.BigDecimal;

public class MateriaPrima {
	private BigDecimal codprod;
	private BigDecimal qtdMP;
	private BigDecimal qtdDisponivel;
	private BigDecimal mpPrincipal;
	private String controle;

	public MateriaPrima(BigDecimal codprod, String controle, BigDecimal qtdMP, BigDecimal qtdDisponivel,
			BigDecimal mpprin) {
		this.codprod = codprod;
		this.controle = controle;
		this.qtdMP = qtdMP;
		this.qtdDisponivel = qtdDisponivel;
		this.mpPrincipal = mpprin;
	}

	public BigDecimal getCodprod() {
		return codprod;
	}

	public void setCodprod(BigDecimal codprod) {
		this.codprod = codprod;
	}

	public String getControle() {
		return controle;
	}

	public void setControle(String controle) {
		this.controle = controle;
	}

	public BigDecimal getQtdMP() {
		return qtdMP;
	}

	public void setQtdMP(BigDecimal qtdMP) {
		this.qtdMP = qtdMP;
	}

	public BigDecimal getQtdDisponivel() {
		return qtdDisponivel;
	}

	public void setQtdDisponivel(BigDecimal qtdDisponivel) {
		this.qtdDisponivel = qtdDisponivel;
	}
	
	

	public BigDecimal getMpPrincipal() {
		return mpPrincipal;
	}

	public void setMpPrincipal(BigDecimal mpPrincipal) {
		this.mpPrincipal = mpPrincipal;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof MateriaPrima) {
			MateriaPrima mp = (MateriaPrima) o;
			return this.codprod.equals(mp.codprod) && this.controle.equals(mp.controle);
		}
		return false;
	}

}
