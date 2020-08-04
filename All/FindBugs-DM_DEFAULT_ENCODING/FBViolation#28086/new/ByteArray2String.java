package teetime.stage;

import java.nio.charset.Charset;

import teetime.framework.ConsumerStage;
import teetime.framework.OutputPort;

public class ByteArray2String extends ConsumerStage<byte[]> {

	private final OutputPort<String> outputPort = this.createOutputPort();

	@Override
	protected void execute(final byte[] element) {
		this.send(this.outputPort, new String(element, Charset.forName("UTF-8")));
	}

	public OutputPort<? extends String> getOutputPort() {
		return this.outputPort;
	}
}
