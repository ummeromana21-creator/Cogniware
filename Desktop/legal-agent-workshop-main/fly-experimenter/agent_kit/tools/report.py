"""File the final report. This ends the run."""
NAME = "file_report"
DESCRIPTION = ("File the final report and end the run: numbered findings with "
               "their numbers, and one conclusion. Use only numbers from tools.")
PARAMETERS = {
    "type": "object",
    "properties": {"findings": {"type": "array", "items": {"type": "string"}},
                   "conclusion": {"type": "string"}},
    "required": ["findings", "conclusion"], "additionalProperties": False,
}


def run(args, ctx):
    ctx.record.set_report({"findings": args["findings"], "conclusion": args["conclusion"]})
    return {"status": "over", "conclusion": args["conclusion"]}
