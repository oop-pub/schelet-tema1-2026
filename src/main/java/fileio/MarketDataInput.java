package fileio;

import java.math.BigDecimal;

/** Stores one data entry from the input JSON file. */
public record MarketDataInput(
        String id,
        String date,
        String interval,
        Double priceAmount,
        Double predictedPriceEurMwh,
        Double spikeProbability,
        BigDecimal quantity,
        Double reservesRequiredMW,
        Double positiveImbalancePrice,
        Double negativeImbalancePrice,
        Double positiveImbalanceVol,
        Double negativeImbalanceVol
) {
}
