package com.univocity.trader.config;

import com.univocity.trader.*;
import com.univocity.trader.account.*;
import com.univocity.trader.simulation.*;
import org.apache.commons.lang3.*;

import java.io.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static com.univocity.trader.config.Utils.*;

/**
 * @author uniVocity Software Pty Ltd - <a href="mailto:dev@univocity.com">dev@univocity.com</a>
 */
public class Simulation extends ConfigurationGroup implements Cloneable {

	private static DateTimeFormatter newFormatter(String pattern) {
		return new DateTimeFormatterBuilder()
				.appendPattern(pattern)
				.parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
				.parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
				.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
				.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
				.toFormatter();
	}

	private static final DateTimeFormatter[] formatters = new DateTimeFormatter[]{
			newFormatter("yyyy-MM-dd HH:mm"),
			newFormatter("yyyy-MM-dd"),
			newFormatter("yyyy-MM"),
			newFormatter("yyyy"),
	};

	private TradingFees tradingFees;
	private LocalDateTime simulationStart;
	private LocalDateTime simulationEnd;
	private boolean cacheCandles = false;

	private Map<String, Double> initialFunds = new ConcurrentHashMap<>();

	private File parametersFile;
	private Class<? extends Parameters> parametersType;

//	simulation.initial.funds=[USDT]2000.0,[ADA;ETH]100.0
//	simulation.parameters.csv=/path/to/your.csv

	public Simulation(ConfigurationRoot parent) {
		super(parent);
	}

	public final LocalDateTime simulationStart() {
		return simulationStart != null ? simulationStart : LocalDateTime.now().minusYears(1);
	}

	public final LocalDateTime simulationEnd() {
		return simulationEnd != null ? simulationEnd : LocalDateTime.now();
	}

	public Simulation simulationStart(LocalDateTime simulationStart) {
		this.simulationStart = simulationStart;
		return this;
	}

	public Simulation simulationEnd(LocalDateTime simulationEnd) {
		this.simulationEnd = simulationEnd;
		return this;
	}

	public Simulation simulationStart(String simulationStart) {
		this.simulationStart = parseDateTime(simulationStart, null);
		return this;
	}

	public Simulation simulationEnd(String simulationEnd) {
		this.simulationEnd = parseDateTime(simulationEnd, null);
		return this;
	}

	public Simulation simulationStart(long time) {
		this.simulationStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
		return this;
	}

	public Simulation simulationEnd(long time) {
		this.simulationEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
		return this;
	}

	public Simulation simulationStart(Instant date) {
		this.simulationStart = date == null ? null : LocalDateTime.ofInstant(date, ZoneId.systemDefault());
		return this;
	}

	public Simulation simulationEnd(Instant date) {
		this.simulationEnd = date == null ? null : LocalDateTime.ofInstant(date, ZoneId.systemDefault());
		return this;
	}

	public Simulation simulationStart(Date date) {
		this.simulationStart = date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
		return this;
	}

	public Simulation simulationEnd(Date date) {
		this.simulationEnd = date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
		return this;
	}


	public Simulation simulationStart(Calendar date) {
		this.simulationStart = date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
		return this;
	}

	public Simulation simulationEnd(Calendar date) {
		this.simulationEnd = date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
		return this;
	}


	@Override
	protected void readProperties(PropertyBasedConfiguration properties) {
		tradingFees(parseTradingFees(properties.getOptionalProperty("simulation.trade.fees")));
		simulationStart(parseDateTime(properties, "simulation.start"));
		simulationEnd(parseDateTime(properties, "simulation.end"));
		cacheCandles(properties.getBoolean("simulation.cache.candles", false));

		parseInitialFunds(properties);
	}

	private void parseInitialFunds(PropertyBasedConfiguration properties) {
		Function<String, Double> f = (amount) -> {
			try {
				return Double.valueOf(amount);
			} catch (NumberFormatException ex) {
				throw new IllegalConfigurationException("Invalid initial funds amount '" + amount + "' defined in property 'simulation.initial.funds'", ex);
			}
		};
		parseGroupSetting(properties, "simulation.initial.funds", f, this::initialAmounts);
	}

	private LocalDateTime parseDateTime(String s, String propertyName) {
		if (StringUtils.isBlank(s)) {
			return null;
		}

		for(DateTimeFormatter formatter : formatters) {
			try {
				return LocalDateTime.parse(s, formatter);
			} catch (Exception e) {
				//ignore
			}
		}

		String property = propertyName == null ? "" : " of property '" + propertyName + "'";
		throw new IllegalConfigurationException("Unrecognized date format in value '" + s + "'" + property + ". Supported formats are: yyyy-MM-dd HH:mm, yyyy-MM-dd, yyyy-MM and yyyy");
	}

	private LocalDateTime parseDateTime(PropertyBasedConfiguration properties, String propertyName) {
		return parseDateTime(properties.getOptionalProperty(propertyName), propertyName);
	}


	private TradingFees parseTradingFees(String fees) {
		if(fees == null){
			return null;
		}
		try {
			if (Character.isDigit(fees.charAt(0))) {
				if (fees.endsWith("%")) {
					fees = StringUtils.substringBefore(fees, "%");
					return SimpleTradingFees.percentage(Double.parseDouble(fees));
				} else {
					return SimpleTradingFees.amount(Double.parseDouble(fees));
				}
			}

			return Utils.findClassAndInstantiate(TradingFees.class, fees);
		} catch (Exception ex) {
			throw new IllegalConfigurationException("Error processing trading fees '" + fees + "' defined in property 'simulation.trade.fees'", ex);
		}
	}

	public final TradingFees tradingFees() {
		return tradingFees;
	}

	public final Simulation tradingFees(TradingFees tradingFees) {
		this.tradingFees = tradingFees;
		return this;
	}

	public final Simulation tradingFeeAmount(double amountPerTrade) {
		this.tradingFees = SimpleTradingFees.amount(amountPerTrade);
		return this;
	}

	public final Simulation tradingFeePercentage(double percentagePerTrade) {
		this.tradingFees = SimpleTradingFees.percentage(percentagePerTrade);
		return this;
	}

	public boolean cacheCandles() {
		return cacheCandles;
	}

	public Simulation cacheCandles(boolean cacheCandles) {
		this.cacheCandles = cacheCandles;
		return this;
	}

	public Simulation initialFunds(double initialFunds) {
		initialAmount("", initialFunds);
		return this;
	}

	public double initialFunds() {
		return initialAmount("");
	}

	public double initialAmount(String symbol) {
		return initialFunds.getOrDefault(symbol, 0.0);
	}

	public Simulation initialAmount(String symbol, double initialAmount) {
		initialFunds.put(symbol, initialAmount);
		return this;
	}

	public Map<String, Double> initialAmounts() {
		return Collections.unmodifiableMap(initialFunds);
	}

	private void initialAmounts(double initialAmount, String... symbols) {
		if (symbols.length == 0) {
			//default to reference currency.
			initialFunds(initialAmount);
		} else {
			for (String symbol : symbols) {
				initialFunds.put(symbol, initialAmount);
			}
		}
	}

	@Override
	public boolean isConfigured() {
		return tradingFees != null && !initialFunds.isEmpty();
	}

	@Override
	public Simulation clone() {
		try {
			Simulation out = (Simulation) super.clone();
			out.initialFunds = new ConcurrentHashMap<>(initialFunds);
			return out;
		} catch (CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
	}
}
