"""One fixed SSM document and one narrowly scoped GitHub OIDC dispatch role."""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from camt_ingest.render_document import DOCUMENT, render


def sub(value):
    return {"Fn::Sub": value}


def render_stack():
    role_policy = {"Version": "2012-10-17", "Statement": [{
        "Effect": "Allow",
        "Principal": {"Federated": sub(
            "arn:${AWS::Partition}:iam::${AWS::AccountId}:oidc-provider/token.actions.githubusercontent.com")},
        "Action": "sts:AssumeRoleWithWebIdentity",
        "Condition": {"StringEquals": {
            "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
            "token.actions.githubusercontent.com:sub":
                "repo:AnDev77@57936145/doChallenge@1307624804:environment:ec2-camt-ingest",
        }},
    }]}
    permissions = {"Version": "2012-10-17", "Statement": [
        {"Effect": "Allow", "Action": "ssm:SendCommand", "Resource": [
            sub("arn:${AWS::Partition}:ssm:${AWS::Region}:${AWS::AccountId}:document/${IngestDocument}"),
            sub("arn:${AWS::Partition}:ec2:${AWS::Region}:${AWS::AccountId}:instance/${InstanceId}"),
        ]},
        {"Effect": "Allow", "Action": "ssm:GetCommandInvocation", "Resource": "*",
         "Condition": {"StringEquals": {"aws:RequestedRegion": {"Ref": "AWS::Region"}}}},
    ]}
    return {"AWSTemplateFormatVersion": "2010-09-09",
            "Description": "Fixed CAMT SEC ingestion only; no arbitrary shell or model approval",
            "Parameters": {"InstanceId": {"Type": "AWS::EC2::Instance::Id"}},
            "Resources": {
                "IngestDocument": {"Type": "AWS::SSM::Document", "Properties": {
                    "Name": DOCUMENT, "DocumentType": "Command", "DocumentFormat": "JSON",
                    "TargetType": "/AWS::EC2::Instance", "Content": render()}},
                "IngestRole": {"Type": "AWS::IAM::Role", "Properties": {
                    "AssumeRolePolicyDocument": role_policy,
                    "Policies": [{"PolicyName": "camt-ingest", "PolicyDocument": permissions}],
                }},
            },
            "Outputs": {
                "RunRoleArn": {"Value": {"Fn::GetAtt": ["IngestRole", "Arn"]}},
                "DocumentName": {"Value": {"Ref": "IngestDocument"}},
                "TargetInstanceId": {"Value": {"Ref": "InstanceId"}},
            }}


if __name__ == "__main__":
    print(json.dumps(render_stack(), indent=2))
