package edu.ucla.cens.mobilityclassifier;

import java.util.ArrayList;
import java.util.List;

public class Classification
{
	private String mode;
	private ArrayList<Double> fft;
	private Double average;
	private Double variance;
	private ArrayList<Double> N95Fft;
	private Double N95Variance;
	private boolean hasFeatures;
	
	public String getMode()
	{
		return mode;
	}
	public void setMode(String mode)
	{
		this.mode = mode;
	}
	public List<Double> getFft()
	{
		return fft;
	}
	public void setFft(List<Double> fft2)
	{
		this.fft = (ArrayList<Double>) fft2;
	}
	public Double getAverage()
	{
		return average;
	}
	public void setAverage(Double average)
	{
		this.average = average;
	}
	public Double getVariance()
	{
		return variance;
	}
	public void setVariance(Double variance)
	{
		this.variance = variance;
	}
	public List<Double> getN95Fft()
	{
		return N95Fft;
	}
	public void setN95Fft(List<Double> n95Fft)
	{
		N95Fft = (ArrayList<Double>) n95Fft;
	}
	public Double getN95Variance()
	{
		return N95Variance;
	}
	public void setN95Variance(Double n95Variance)
	{
		N95Variance = n95Variance;
	}
	public boolean hasFeatures()
	{
		return hasFeatures;
	}
	public void setHasFeatures(boolean hasFeatures)
	{
		this.hasFeatures = hasFeatures;
	}
		
}
