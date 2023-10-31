package br.com.sankhya.apontamentoleitor.utils;

import java.math.BigDecimal;

public class MateriaPrima {
	private BigDecimal codprod;
	private BigDecimal qtdMP;
	private BigDecimal qtdDisponivel;
	private String controle;
	private String mpProd;

	public MateriaPrima(BigDecimal codprod, String controle, BigDecimal qtdMP, BigDecimal qtdDisponivel,
			BigDecimal mpprin) {
		this.codprod = codprod;
		this.controle = controle;
		this.qtdMP = qtdMP;
		this.qtdDisponivel = qtdDisponivel;
		this.mpProd = String.valueOf(mpprin)+ codprod;
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
	
	

	public String getMpProd() {
		return mpProd;
	}

	public void setMpProd(String mpProd) {
		this.mpProd = mpProd;
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
