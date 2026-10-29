package fileio;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Stores query parameters or published data attached to a command.
 */
@Data
@NoArgsConstructor
public final class CommandParametersInput {
    private String clientTier;
    private String dataType;
    private String target;
    private String start;
    private String end;
    private List<MarketDataInput> data;
}
