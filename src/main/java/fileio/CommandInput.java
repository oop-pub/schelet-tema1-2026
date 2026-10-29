package fileio;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a command name, timestamp and optional parameters from JSON.
 */
@Data
@NoArgsConstructor
public final class CommandInput {
    private String command;
    private String timestamp;
    private CommandParametersInput parameters;
}
