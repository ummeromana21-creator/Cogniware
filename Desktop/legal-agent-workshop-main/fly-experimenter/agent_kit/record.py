"""The run's experiment record: card, trials, observations, report."""


class Record:
    def __init__(self, data=None):
        data = data or {}
        self.card = data.get("card")
        self.trials = data.get("trials", [])
        self.observations = data.get("observations", [])
        self.report = data.get("report")

    def set_card(self, card):
        self.card = card

    def add_trial(self, trial):
        self.trials.append(trial)

    def add_observation(self, text):
        self.observations.append(text)

    def set_report(self, report):
        self.report = report

    def to_dict(self):
        return {"card": self.card, "trials": self.trials,
                "observations": self.observations, "report": self.report}
