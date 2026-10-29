package fileio;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the commands and their parameters from an input JSON file.
 */
public final class InputLoader {
    private final List<CommandInput> commands;

    /**
     * Reads and deserializes the command list.
     *
     * @param inputPath path to the input JSON file
     * @throws IOException if the file cannot be read or the command list is missing
     */
    public InputLoader(final String inputPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputRoot input = mapper.readValue(new File(inputPath), InputRoot.class);
        if (input.getCommands() == null) {
            throw new IOException("Input field 'commands' must be an array");
        }
        commands = new ArrayList<>(input.getCommands());
    }

    /**
     * Returns the parsed commands in input order.
     *
     * @return an unmodifiable copy of the command list
     */
    public List<CommandInput> getCommands() {
        return List.copyOf(commands);
    }

}
