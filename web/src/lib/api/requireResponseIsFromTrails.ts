export default function (response: Response) {
    if (response.headers.get("X-Trails-Origin") !== "trails") throw new Error("Response is not from Trails");
}