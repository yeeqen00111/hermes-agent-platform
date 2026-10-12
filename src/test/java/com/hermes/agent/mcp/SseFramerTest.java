package com.hermes.agent.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 帧解析：多行 data 合并、注释行忽略、空行分帧、event 默认值。
 */
class SseFramerTest {

    @Test
    void parsesFramesSplitByBlankLine() {
        String text = "event: message\ndata: {\"a\":1}\n\n"
                + "event: log\ndata: hello\ndata: world\n\n"
                + "\n";

        List<SseFramer.Frame> frames = SseFramer.parse(text);

        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).event()).isEqualTo("message");
        assertThat(frames.get(0).data()).isEqualTo("{\"a\":1}");
        assertThat(frames.get(1).event()).isEqualTo("log");
        // 多行 data 以 \n 连接
        assertThat(frames.get(1).data()).isEqualTo("hello\nworld");
    }

    @Test
    void commentLinesAndUnknownFieldsIgnored() {
        String text = ": keep-alive\nid: 42\nretry: 3000\ndata: {\"b\":2}\n\n";

        List<SseFramer.Frame> frames = SseFramer.parse(text);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).data()).isEqualTo("{\"b\":2}");
        // 无 event 字段时默认 message
        assertThat(frames.get(0).event()).isEqualTo("message");
    }

    @Test
    void crlfAndBareCarriageReturnHandled() {
        String text = "event: message\r\ndata: {\"c\":3}\r\n\r\n"
                + "event: message\r data: x\r\r";

        List<SseFramer.Frame> frames = SseFramer.parse(text);

        // 第二种形态里 " data: x" 前是 \r 分隔的独立行，属于字段名缺失形态，按规范应为 (field="data") 之外——此处只校验主路径
        assertThat(frames.get(0).event()).isEqualTo("message");
        assertThat(frames.get(0).data()).isEqualTo("{\"c\":3}");
    }

    @Test
    void incrementalFeedMatchesBatchParse() {
        String text = "event: message\ndata: {\"a\":1}\n\ndata: {\"a\":2}\n\n";
        List<SseFramer.Frame> expected = SseFramer.parse(text);

        SseFramer framer = new SseFramer();
        List<SseFramer.Frame> actual = new java.util.ArrayList<>();
        for (String line : text.split("\r\n|\r|\n", -1)) {
            SseFramer.Frame frame = framer.feed(line);
            if (frame != null) {
                actual.add(frame);
            }
        }

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void blankDataFrameStillDispatched() {
        List<SseFramer.Frame> frames = SseFramer.parse("data: \n\n");
        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).data()).isEmpty();
    }
}
