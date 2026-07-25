import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import "dayjs/locale/de";

dayjs.extend(relativeTime);
dayjs.locale("de");

export default dayjs;
