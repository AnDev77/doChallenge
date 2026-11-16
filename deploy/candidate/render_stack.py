"""Render CloudFormation for the separate EC2 candidate roles and SSM document."""

import json

from render_document import DOCUMENT, render


def sub(value):
    return {"Fn::Sub": value}


def policy(*statements):
    return {"Version": "2012-10-17", "Statement": list(statements)}


def allow(actions, resources, **conditions):
    result = {"Effect": "Allow", "Action": actions, "Resource": resources}
    if conditions:
        result["Condition"] = conditions
    return result


def github_role(environment, permissions):
    return {
        "Type": "AWS::IAM::Role",
        "Properties": {
            "AssumeRolePolicyDocument": policy({
                "Effect": "Allow",
                "Principal": {"Federated": sub(
                    "arn:${AWS::Partition}:iam::${AWS::AccountId}:oidc-provider/token.actions.githubusercontent.com"
                )},
                "Action": "sts:AssumeRoleWithWebIdentity",
                "Condition": {"StringEquals": {
                    "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
                    "token.actions.githubusercontent.com:sub": (
                        "repo:AnDev77@57936145/doChallenge@1307624804:environment:" + environment
                    ),
                }},
            }),
            "Policies": [{"PolicyName": "candidate", "PolicyDocument": permissions}],
        },
    }


def render_stack():
    ecr = {"Fn::GetAtt": ["CandidateRepository", "Arn"]}
    ecr_login = allow("ecr:GetAuthorizationToken", "*", StringEquals={
        "aws:RequestedRegion": {"Ref": "AWS::Region"},
    })
    return {
        "AWSTemplateFormatVersion": "2010-09-09",
        "Description": "ShockGraph isolated candidate only. No changes to HTTP or production Compose.",
        "Parameters": {
            "InstanceId": {"Type": "AWS::EC2::Instance::Id"},
            "Ec2RoleName": {"Type": "String", "AllowedPattern": r"^[\w+=,.@-]{1,64}$"},
        },
        "Resources": {
            "CandidateRepository": {
                "Type": "AWS::ECR::Repository",
                "DeletionPolicy": "Retain",
                "UpdateReplacePolicy": "Retain",
                "Properties": {"RepositoryName": "shockgraph-java-candidate", "ImageTagMutability": "IMMUTABLE"},
            },
            "CandidateDocument": {
                "Type": "AWS::SSM::Document",
                "Properties": {
                    "Name": DOCUMENT,
                    "DocumentType": "Command",
                    "DocumentFormat": "JSON",
                    "TargetType": "/AWS::EC2::Instance",
                    "Content": render(),
                },
            },
            "PublishRole": github_role("ec2-candidate-publish", policy(
                ecr_login,
                allow([
                    "ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart",
                    "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:DescribeImages",
                ], ecr),
            )),
            "RunRole": github_role("ec2-candidate", policy(
                allow("ssm:SendCommand", [
                    sub("arn:${AWS::Partition}:ssm:${AWS::Region}:${AWS::AccountId}:document/${CandidateDocument}"),
                    sub("arn:${AWS::Partition}:ec2:${AWS::Region}:${AWS::AccountId}:instance/${InstanceId}"),
                ]),
                allow("ssm:GetCommandInvocation", "*", StringEquals={
                    "aws:RequestedRegion": {"Ref": "AWS::Region"},
                }),
            )),
            "CandidatePullPolicy": {
                "Type": "AWS::IAM::Policy",
                "Properties": {
                    "PolicyName": "shockgraph-candidate-ecr-pull",
                    "Roles": [{"Ref": "Ec2RoleName"}],
                    "PolicyDocument": policy(ecr_login, allow(
                        ["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"],
                        ecr,
                    )),
                },
            },
        },
        "Outputs": {
            "PublishRoleArn": {"Value": {"Fn::GetAtt": ["PublishRole", "Arn"]}},
            "RunRoleArn": {"Value": {"Fn::GetAtt": ["RunRole", "Arn"]}},
            "DocumentName": {"Value": {"Ref": "CandidateDocument"}},
            "TargetInstanceId": {"Value": {"Ref": "InstanceId"}},
        },
    }


if __name__ == "__main__":
    print(json.dumps(render_stack(), indent=2))
