package fileio;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Represents the top-level JSON object containing the commands array. */
@Data
@NoArgsConstructor
public final class InputRoot {
    private List<CommandInput> commands;
}
